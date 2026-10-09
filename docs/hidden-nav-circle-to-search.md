# Hidden gesture handle Circle to Search

The existing international assistant / native Circle to Search option also enables
the hidden-handle native launcher path on the inspected OnePlus 15 CN build
`PLK110_17.0.0.105(CN01)` (launcher 17.3.12, version code 170030012).
Other builds retain the existing compatibility route. All reflected signatures
are checked before installing the hooks; partial installation is rolled back.

The launcher capability predicates allow the native controller to run only when
its live observers report gesture navigation, a hidden handle, and Circle to
Search enabled. Native touch recognition, movement cancellation, press animation,
and assistant dispatch remain intact. On this launcher the native dispatcher
uses its actual SystemUI proxy; the older obfuscated proxy override is skipped.

Settings receives the same hidden-handle capability. Its original confirmation
dialogs then skip the branches that disable Circle to Search when hiding the
handle, or show the handle when enabling Circle to Search. This does not force
the user's Circle to Search setting on, and does not replace secure-setting writes.
No corresponding reset code exists in ONextBox itself to delete.

Validation: the extracted hooks were tested on the above rooted CN device with
the handle hidden and the user confirmed bottom-area long press opens Circle to
Search. Observer tests cover all eight combinations, live changes, missing
instances, observer exceptions, and incompatible signatures. Enable the existing
international assistant option, select Google as the digital assistant, enable
Circle to Search in navigation settings, and restart the relevant processes.
