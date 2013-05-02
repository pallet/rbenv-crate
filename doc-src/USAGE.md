## Usage

The `server-spec` function provides a convenient pallet server spec for
rbenv-crate.  It takes a single map as an argument, specifying configuration
choices, as described below for the `settings` function.  You can use this
in your own group or server specs in the :extends clause.

```clj
(require '[pallet.crate.rbenv :as rbenv])
(group-spec my-rbenv-crate-group
  :extends [(rbenv/server-spec {})])
```

While `server-spec` provides an all-in-one function, you can use the individual
plan functions as you see fit.

The `settings` function provides a plan function that should be called in the
`:settings` phase.  The function puts the configuration options into the pallet
session, where they can be found by the other crate functions, or by other
crates wanting to interact with rbenv-crate.

The `install` function is responsible for actually installing rbenv-crate.

The `configure` function writes the rbenv initialisation to `.profile`.  The use
of this is optional, as `rbenv-init` provides a script function to initialise
rbenv.

The `rbenv-cmd` plan function can execute arbitrary rbenv commands.
