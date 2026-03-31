# 👋🧩 Patcheddit

Morphe compatible patches for third party Reddit apps.

## ❓ About

Patcheddit is a Morphe compatible patch bundle that supports patching the OAuth login information for 3rd party reddit apps.

In addition, patches for some apps contain additional features that were not present in ReVanced.

## Differences from official patch set

### All clients
* Client ID, redirect URI and user agent are all patchable.
    * By default, redirect URI and user agent are set to the values used by RedReader.
    * See https://github.com/ReVanced/revanced-patches/pull/4551 for context.

### Boost for Reddit
* The content of deleted Reddit posts and comments can be loaded from [Project Arctic Shift](https://github.com/ArthurHeitmann/arctic_shift).
    * This only works for text and images, and not all content can be restored. Videos are unlikely to be available.
    * Posts/comments that were deleted are marked as follows:
        * 🗑️ if removed by the author
        * 🧹 if removed by a subreddit mod
        * 🚓 if removed by reddit admins
        * 🤖 if removed by anti-spam
        * ©️ if removed due to a takedown notice
        * 👿 if removed by Anti-Evil Operations
        * 🚫 if the subreddit is banned
    * Banned subreddits can be browsed with this functionality; however, rules/wiki pages are unavailable and the only sort option is by new.
    * In order to view a deleted post, you need to already have a direct link to the post. Subreddit feeds will not show deleted posts (unless the subreddit was banned); this is because of the lack of sorting options and the high likelihood of seeing a ton of spam posts.
    * Currently, this functionality is not working on user profiles. Removed posts you encounter on user pages need to be opened and refreshed for their content to load.
    * This feature makes extra network requests, so posts will load a little slower. In particular, retrieving images from the Wayback Machine is heavily ratelimited.
        * Caching is implemented to try and limit the impact of this.
* Imgur images and albums are automatically loaded from the Wayback Machine if a 404 is detected.
* The context menu for posts now contains additional options for opening a site in the Wayback Machine or archive.is. This is useful for if the undeleting functionality has issues or you want to bypass a content paywall.
* Fixes audio in downloaded videos for all videos using a proper MPD parser. The official fix fixes this for new videos but breaks it for older ones.

### reddit is fun
* Imgur album URLs are rewritten to use the newer format.
* Enables pro features without ads.

## 🚀 Get started

1. Install [Morphe Manager](https://morphe.software/) and switch it to advanced/expert mode.
2. Click [this link](https://morphe.software/add-source?github=wchill/patcheddit) to add this patch source to Morphe Manager.
3. In Morphe Manager, find the app you want to patch.
   * The supported apps are Boost, RIF, Sync, Relay, BaconReader, Joey, Infinity+, Continuum, and the cygnusx1 fork of Slide.
4. Get the APK for your preferred, supported 3rd party reddit app. If you don't have it, you can download it from in the app. APKM bundles (aka split APKs) will also work.
   * You should get the last available version for your reddit app, unless you are patching Relay. Relay v10.2.40 should be used instead of the latest.
   * Continuum and the cygnusx1 fork of Slide need to be downloaded manually from their respective GitHub releases pages, as they are not available on the Play Store.
       * Continuum: https://github.com/cygnusx-1-org/continuum/releases
       * Slide (cygnusx1 fork): https://github.com/cygnusx-1-org/slide/releases
5. Select desired patches, then patch.
   * You will need to have an OAuth client ID from https://www.reddit.com/prefs/apps/, however reddit is no longer issuing new ones without an approval process that usually results in denial. In order to work around this, see the subsection `What if I don't have a client ID?` below.
   * Make sure that the redirect URI in `Spoof client` options and on https://www.reddit.com/prefs/apps/ match. If you don't know what to set the redirect URI/user agent to, use the values from `What if I don't have a client ID?`
6. Once patching is complete, install the app and set it up as usual.

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
4. Select your other desired patches and patch as normal.

If you're wondering why I'm asking you to go through these steps instead of just including the client ID in the patch, it's because including the client ID in the patch would make it accessible to web scrapers and would likely result in the client ID being revoked, breaking the patch for everyone.

### Resolving login problems

I am unable to select the username/password fields on the login page - my keyboard pops up and then disappears quickly

* Switch your phone to landscape mode. There is a cookie banner that needs to be dismissed, but it is bugged and only shows up on landscape mode.

I either get `{}` or `Error: Invalid request to Oauth API`

* Your redirect URI is probably wrong. It has to match exactly between the patching settings and your reddit installed app. If you're reusing an installed app you made before, then update the URI at https://www.reddit.com/prefs/apps/. Make sure you don't add `/` at the end.

I cannot login, reddit is telling me that my username/password are invalid even though they are correct

* Reddit doesn't like the network you're logging in from (corporate/work network, country with banned IP ranges due to abuse, etc). Try using a VPN or switching to cellular data.
* Alternatively, this problem may go away if you restart your phone, clear cookies for Chrome, clear app data for Android System WebView, or update Android System WebView to the latest version.
* See https://github.com/cygnusx-1-org/continuum/blob/master/SETUP.md#common-errors

I cannot hit Accept on the Authorize screen

* Change your reddit site language to English. There are reports that this screen does not work properly otherwise.

I still get 403 Blocked

* If you filled your user agent in with garbage, it will eventually be blocked by reddit. Also, reddit blocks any mention of rubenmayayo in the user agent.
* Your user agent can also be blocked due to other terms: a user reported that he was getting 403 after patching, and it turned out to be because his username included `isfun` (which triggers a block from reddit's side due to them blocking the app Reddit is Fun).

I get 401 when I open the app

* You probably created a web app instead of an installed app. Delete the app, create an installed app and then repatch with the new client ID.
* Don't use autofill when logging into Reddit. Manually type in or copy+paste your password.

I'm getting 400 Bad Request while logged in

* Either try logging out and logging back in, or uninstall the app (back up your settings first) then reinstall it.

I get a `null: null` error when I open the app

* Your client ID is incorrect, check that you copied it correctly.



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

  > This app uses code from Patcheddit. To learn more, visit https://reddit.com/r/patcheddit

- **Name Restriction (7c):** The name **"Patcheddit"** may not be used for derivative works.  
  Derivatives must adopt a distinct identity unrelated to "Patcheddit".

The Morphe project (https://github.com/MorpheApp) may use code from this project without displaying the attribution notice.

See the [LICENSE](LICENSE) file for the full GPLv3 terms and the [NOTICE](NOTICE) file for full conditions of GPLv3 Section 7
