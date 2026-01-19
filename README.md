# 👋🧩 Patcheddit

Morphe patches for Reddit apps.

&nbsp;
## ❓ About

TODO: Add description of patches.

In the meantime, see https://github.com/wchill/revanced-patches for a list of the changes present in this patch.

## 🚀 Get started

1. Install [Morphe Manager](https://morphe.software/) and switch it to advanced/expert mode.
2. In Morphe Manager, in the Patch Bundles tab, select `Enter URL` and copy-paste **ONE** of the following URLs:
   * Stable branch: `https://raw.githubusercontent.com/wchill/patcheddit/refs/heads/main/patches-bundle.json`
   * Dev branch: `https://raw.githubusercontent.com/wchill/patcheddit/refs/heads/dev/patches-bundle.json`
3. Get the APK for your preferred, supported 3rd party reddit app. If you don't have it, you can download it from https://apkmirror.com. APKM bundles (aka split APKs) will also work.
   * The supported apps are Boost, RIF, Sync, Relay, BaconReader, and Joey.
   * Infinity and Slide are open source and have actively maintained forks; see https://github.com/cygnusx-1-org/continuum and https://github.com/cygnusx-1-org/Slide instead of using these patches.
   * You should get the last available version for your reddit app, unless you are patching Relay. Relay v10.2.40 should be used instead of the latest.
4. In Morphe Manager, in the Apps tab, tap the + (plus) icon > Select from storage > the APK file you downloaded in step 3.
5. Select appropriate patches and hit Save, then Patch.
   * Make sure you have `Spoof client` selected, at a minimum. Make sure to fill in all the relevant fields to appropriate values.
   * You will need to have an OAuth client ID from https://www.reddit.com/prefs/apps/, however reddit is no longer issuing new ones without an approval process that usually results in denial. In order to work around this, see the subsection `What if I don't have a client ID?` below.
7. Once patching is complete, install the app and set it up as usual.

For assistance with patching, please post a thread in [/r/Patcheddit](https://www.reddit.com/r/Patcheddit/) or [/r/MorpheApp](https://www.reddit.com/r/MorpheApp/). Only use the GitHub issue tracker for legitimate bug reports or feature requests.

### What if I don't have a client ID?

You can use the client ID from other working 3rd party reddit apps (sadly not the official one). I recommend using the one from RedReader, as they have a special deal with reddit to use the API for free due to accessibility reasons. (The same steps can be followed with other apps such as Infinity+, but the developer will pay for the API calls you use and I do not recommend this.)

1. Install RedReader from the Play Store and login with it.
2. After you login, you should get an email with the title `You’ve authorized a new app in your Reddit account`. Look for `App ID`: in that email; note the random looking string as it will be used in the next step.
   * You can uninstall RedReader after this if you want.
3. In Morphe Manager, make sure you set these values for the `Spoof client` patch. Make sure there are no extra spaces or slashes at the end.
   * `OAuth client ID` - set this to the random looking string from the email.
   * `Redirect URI` - set this to `redreader://rr_oauth_redir` (or the appropriate value if you got the client ID from a different app).
   * `User agent` - set this to `org.quantumbadger.redreader/1.25.1`. Ignore the description that says what the user agent format should be.
   * Depending on your app, you may not need to set all of these values for it to work, but it is highly recommended that you do so.
5. Select your other desired patches and patch as normal.

## 🧑‍💻 Development

To develop and release Patcheddit, some things need to be considered:

- Development starts in feature branches. Once a feature branch is ready, it is squashed and merged into the `dev` branch
- The `dev` branch is merged into the `main` branch once it is ready for release
- Semantic versioning is used to version Patcheddit. Patcheddit has a public API for other patches to use
- Semantic commit messages are used for commits
- Commits on the `dev` branch and `main` branch are automatically released
via the [release.yml](.github/workflows/release.yml) workflow, which is also responsible for generating the changelog
and updating the version of Patcheddit. It is triggered by pushing to the `dev` or `main` branch.
The workflow uses the `publish` task to publish the release of Patcheddit
- The `buildAndroid` task is used to build Patcheddit so that it can be used on Android.
The `publish` task depends on the `buildAndroid` task, so it will be run automatically when publishing a release.

## 📚 Everything else

### 📙 Contributing

Thank you for considering contributing to Patcheddit.  
You can find the contribution guidelines [here](CONTRIBUTING.md).

### 🛠️ Building

To build Patcheddit,
you can follow the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation).

## 📜 License

Patcheddit is licensed under the [GNU General Public License v3.0](LICENSE), with additional conditions under Section 7:

- **Attribution (7b):** Any use of this code, including derivatives, must display a visible notice:

  > This app uses code from Patcheddit. To learn more, visit https://github.com/wchill/patcheddit or https://reddit.com/r/patcheddit
  > This app uses code from Morphe. To learn more, visit http://morphe.software

- **Name Restriction (7c):** The name **"Patcheddit"** may not be used for derivative works.  
  Derivatives must adopt a distinct identity unrelated to "Patcheddit".

The Morphe project (https://github.com/MorpheApp) may use code from this project without displaying the attribution notice.

See the [LICENSE](LICENSE) file for the full GPLv3 terms and the [NOTICE](NOTICE) file for full conditions of GPLv3 Section 7
