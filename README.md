# 👋🧩 Patcheddit

Morphe patches for Reddit apps.

&nbsp;
## ❓ About

TODO: Add description of patches.

## 🚀 Get started

TODO: Write up steps on how to patch using URM/Morphe Manager.

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

## 📜 Licence

Patcheddit is licensed under the [GNU GPL v3.0](https://www.gnu.org/licenses/gpl-3.0.html), with additional conditions under Section 7:

- **Attribution (7b):** Any use of this code, including derivatives, must display a visible notice:

  > This app uses code from Patcheddit. To learn more, visit https://github.com/wchill/patcheddit or https://reddit.com/r/patcheddit
  > This app uses code from Morphe. To learn more, visit http://morphe.software

- **Name Restriction (7c):** The name **"Patcheddit"** may not be used for derivative works.  
  Derivatives must adopt a distinct identity unrelated to "Patcheddit".

See the [LICENSE](./LICENSE) file for full terms.
