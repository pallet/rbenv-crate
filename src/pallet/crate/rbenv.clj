(ns pallet.crate.rbenv
  "A [pallet](https://palletops.com/) crate to install and configure rbenv.

## Links
 [rbenv](https://github.com/sstephenson/rbenv/)
"
  (:require
   [clj-schema.schema :refer [def-map-schema map-schema optional-path
                              sequence-of]]
   [clojure.string :as string]
   [clojure.tools.logging :refer [debugf]]
   [pallet.action :refer [with-action-options]]
   [pallet.actions :refer [directory exec-checked-script packages plan-when-not
                           remote-directory remote-file]]
   [pallet.api :refer [plan-fn] :as api]
   [pallet.contracts :refer [any-value check-spec]]
   [pallet.crate :refer [admin-user assoc-settings defmethod-plan defplan
                         get-settings]]
   [pallet.crate-install :as crate-install]
   [pallet.script :refer [defimpl defscript]]
   [pallet.stevedore :refer [fragment]]
   [pallet.script.lib :refer [config-root file user-home]]
   [pallet.utils :refer [apply-map]]
   [pallet.version-dispatch :refer [defmethod-version-plan
                                    defmulti-version-plan]]))

;;; # Settings
(def-map-schema rbenv-settings-schema
  [[:urls] (map-schema :loose [])
   [:versions] (map-schema :loose [])
   [:user] string?
   [:install-strategy] keyword?
   [:install-dir] string?
   [:plugins-dir] string?
   [:plugins] (sequence-of keyword?)
   (optional-path [:install-ruby]) any-value])

(defmacro check-rbenv-settings
  [m]
  (check-spec m `rbenv-settings-schema &form))

(defn default-settings
  "Provides default settings, that are merged with any user supplied settings."
  []
  (let [user (:username (admin-user))]
    {:urls {:rbenv "https://github.com/sstephenson/rbenv/tarball/v%s"
           :ruby-build "https://github.com/sstephenson/ruby-build/tarball/v%s"}
     :versions {:rbenv "0.4.0"
               :ruby-build "20130501"}
     :user user
     :install-dir (fragment (file (user-home ~user) ".rbenv"))
     :plugins [:ruby-build]}))

(defmulti-version-plan settings-map [version settings])

(defmethod-version-plan
    settings-map {:os :linux}
    [os os-version version settings]
  (cond
   (:install-strategy settings) settings
   :else (-> settings
             (assoc :install-strategy ::archive))))

(defn finalise-settings
  "Fill in any blanks for the settings"
  [{:keys [install-dir] :as settings}]
  (-> settings
      (update-in [:plugins-dir] #(or % (str install-dir "/plugins")))))

(defplan settings
  "Settings for rbenv-crate"
  [{:keys [instance-id] :as settings}]
  (let [settings (merge (default-settings) (dissoc settings :instance-id))
        settings (settings-map (:version settings) settings)
        settings (finalise-settings settings)]
    (check-rbenv-settings settings)
    (debugf "rbenv settings %s" settings)
    (assoc-settings :rbenv settings {:instance-id instance-id})))

;;; # Install
(defmethod-plan crate-install/install ::archive
  [facility instance-id]
  (let [{:keys [install-dir plugins plugins-dir urls versions user]
         :as settings}
        (get-settings facility {:instance-id instance-id})
        config (fragment (file (user-home ~user) ".ssh" "config"))]
    (check-rbenv-settings settings)
    (remote-directory install-dir :owner user
                      :url (format (urls :rbenv) (versions :rbenv)))
    (doseq [plugin plugins]
      (remote-directory
       (fragment (file ~plugins-dir ~(name plugin)))
       :owner user :url (format (get urls plugin) (get versions plugin))))))

(defplan install
  "Install rbenv-crate"
  [{:keys [instance-id]}]
  (let [settings (get-settings :rbenv {:instance-id instance-id})]
    (crate-install/install :rbenv instance-id)))

;;; # Configure
(defplan configure
  "Write all config files"
  [{:keys [instance-id] :as options}]
  (let [{:keys [user install-dir] :as settings} (get-settings :rbenv options)
        bashrc (fragment (file (user-home ~user) ".bashrc"))]
    (with-action-options {:sudo-user user}
      (exec-checked-script
       (str "rbenv configure")
       (if (not ("fgrep" (str "'" ~install-dir "'") ~bashrc))
         (do
           (println ~(str "export PATH=\"" install-dir "/bin:\\$PATH\"")
                    ">>" ~bashrc)
           (println ~(str "'eval \"$(" install-dir "/bin/rbenv init -)\"'")
                    ">>" ~bashrc)))))))

;;; # rbenv commands
(defscript rbenv [args {:keys [instance-id] :as options}])

(defimpl rbenv :default [args {:keys [instance-id] :as options}]
  ~(let [{:keys [install-dir] :as settings} (get-settings :rbenv options)
         rbenv (fragment (file ~install-dir "bin" "rbenv"))]
     (fragment (~(if-let [env (:env options)]
                   (fragment
                    (~(string/join " " (map (fn [[k v]] (str k "=" v)) env))
                     (~rbenv ~@args)))
                   (fragment (~rbenv ~@args)))))))

(defn rbenv-init
  "A script function to initialise rbenv."
  [options]
  (let [{:keys [install-dir] :as settings} (get-settings :rbenv options)
        path (fragment (file ~install-dir "bin"))]
    (when-not settings
      (throw (ex-info (str "No settings specified: " (pr-str options))
                      {:options options})))
    (fragment
     (set! RBENV_ROOT (quoted ~install-dir))
     ("export" RBENV_ROOT)
     (set! PATH (str ~path ":" @PATH))
     ("eval" (quoted @(rbenv [init "-"] ~options))))))

(defplan rbenv-cmd
  "Run rbenv.  You can pass a map of environment variables with the `:env`
  option."
  [args {:keys [env instance-id] :as options}]
  (let [{:keys [user] :as settings} (get-settings :rbenv options)]
    (with-action-options {:sudo-user user}
      (exec-checked-script
       (str "rbenv " (string/join " " args))
       (rbenv-init ~options)
       (rbenv [~@args] ~options)
       (rbenv [rehash] ~options)))))

(defplan build-packages
  "Call packages for required build packages.  Options are passed to the
packages call."
  [{:as options}]
  (apply-map
   packages
   :apt ["build-essential" "libssl-dev" "zlib1g-dev"]
   :yum ["zlib-devel"]
   options))

(defplan install-ruby
  "Install ruby with rbenv"
  [version & {:keys [instance-id] :as options}]
  (let [{:keys [install-dir user] :as settings} (get-settings :rbenv options)
        dir (fragment (file ~install-dir "versions" ~version))]
    (build-packages {})
    (with-action-options {:sudo-user user}
      (exec-checked-script
       (str "rbenv install " version)
       (if (not (directory? ~dir))
         (do
           (rbenv-init ~options)
           (rbenv ["install" ~version] ~options)))))))

(defplan local
  "Set the local ruby version"
  [version & {:keys [instance-id] :as options}]
  (rbenv-cmd ["local" version] options))

;;; # Server spec
(defn server-spec
  "Returns a server-spec that installs and configures rbenv-crate."
  [settings & {:keys [instance-id] :as options}]
  (api/server-spec
   :phases
   {:settings (plan-fn
                (pallet.crate.rbenv/settings (merge settings options)))
    :install (plan-fn
               (install options)
               ;; configure here so we can use install-ruby in the install phase
               (configure options))}))
