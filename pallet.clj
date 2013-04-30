;;; Pallet project configuration file

(require
 '[pallet.crate.rbenv-test :refer [test-spec]]
 '[pallet.crates.test-nodes :refer [node-specs]])

(defproject lein-crate
  :provider node-specs                  ; supported pallet nodes
  :groups [test-spec])
