# WeKit reference

The launcher entry design was informed by https://github.com/Ujhhgtg/WeKit
(commit 650feba): a distinct launcher alias and an Xposed MODULE_SETTINGS
Activity entry, with the icon action exposed in the top-right menu.
Implementation here uses Android's public PackageManager APIs and retains
PureStart's confirmation and recovery receiver; no WeKit source code is copied.
WeKit is distributed under its own GPL-3.0 licence; see its repository.
