(ns pallet.crate.rbenv-test
  (:require
   [clojure.test :refer :all]
   [pallet.actions :refer [exec-checked-script exec-script*]]
   [pallet.api :refer [group-spec plan-fn]]
   [pallet.build-actions :refer [build-actions build-session]]
   [pallet.crate.git :as git]
   [pallet.crate.rbenv :as rbenv]
   [pallet.script-test :refer [is-true testing-script]]
   [pallet.stevedore :refer [fragment]]))

(deftest rbenv-cmd-test
  (is
   (script-no-comment=
    (first
     (build-actions {:phase-context "rbenv-cmd"}
       (exec-checked-script
        "rbenv rehash x"
        ~(fragment
          (set! RBENV_ROOT (quoted "x"))
          ("export" RBENV_ROOT)
          (set! PATH "x/bin:${PATH}")
          ("eval"
           (quoted
            @("A=b x/bin/rbenv init -"))))
        ("A=b x/bin/rbenv rehash x")
        ("A=b x/bin/rbenv rehash"))))
    (first (build-actions {}
             (rbenv/settings {:install-dir "x"})
             (rbenv/rbenv-cmd '[rehash x] {:env {"A" "b"}}))))))

(def test-spec
  (group-spec "rbenv"
    :extends [(git/server-spec {}) (rbenv/server-spec {})]
    :phases {:install (plan-fn
                        (rbenv/install-ruby "1.9.3-p392")
                        (rbenv/local "1.9.3-p392"))
             :test (plan-fn
                     (exec-script*
                      (testing-script "Ruby is installed"
                        (is-true
                         (do
                           (rbenv/rbenv-init {})
                           ("ruby" "--version"))
                         "Verify ruby is installed"))))}
    :roles #{:live-test :rbenv}))
