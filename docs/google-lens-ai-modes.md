# Google Lens Talk / Create compatibility

The existing international assistant / native Circle to Search option also enables
Lens Talk and Create eligibility on compatible Google implementations. There is
no version-code allowlist: target classes, method signatures and caller checks
determine whether the hooks can be installed. Add Google to the module scope and
restart its processes after changing the option. The original implementation was
tested with Google `17.65.17.ve.arm64`, version code `301818946`. Other versions
can work when these interfaces match, but renamed obfuscated targets are not
automatically discovered and incompatible implementations are skipped safely.
This feature is independent of the Hey Google switch and the hidden-handle patch.

Two pure eligibility predicates are intercepted: `ctwn.c()` for Create and
`dsky.f()` for Talk. Target signatures and the inspected callers are checked
before installation. ART deoptimization is limited to those callers; partial
installation rolls both hooks back. No account data or persisted Phenotype
configuration is modified. Disabling the option restores the original predicates.

Comparison used a OnePlus 15 CN and OnePlus 13 global with identical Google APKs,
the same Google account and proxy exit. The CN client explicitly disabled Google
app flag `45679856` and lacked enabled flag `45730537`; the global client used the
former's true default and enabled the latter. Runtime inspection linked these
differences to the Lens eligibility gates. This identifies a client-side cause,
but does not establish why Google's rollout assigned different flags.

Validation: the extracted two-hook implementation made both entries appear on
the CN device; the user verified both functions worked. Removing the hooks made
the entries disappear again. Changes to three unrelated Lens flags had no effect
and were restored before testing this implementation. Backend, account, network
and regional availability still determine whether the exposed features can run.
