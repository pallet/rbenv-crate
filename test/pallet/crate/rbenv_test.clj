(ns pallet.crate.rbenv-test
  (:require
   [clojure.test :refer :all]
   [pallet.actions :refer [exec-script*]]
   [pallet.api :refer [group-spec plan-fn]]
   [pallet.crate.git :as git]
   [pallet.crate.rbenv :as rbenv]
   [pallet.script-test :refer [is-true testing-script]]))

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
