# 👋🧩 Morphe Patches template

Template repository for Morphe Patches.

## ❓ About

This is a template to create a new Morphe Patches repository.  
The repository can have multiple patches, and patches from other repositories can be used together.

For an example repository, see [Morphe Patches](https://github.com/morphe/morphe-patches).

## 🚀 Get started

To start using this template, follow these steps:

1. [Create a new repository using this template](https://github.com/new?template_name=morphe-patches-template&template_owner=Morphe)
2. Set up the [build.gradle.kts](patches/build.gradle.kts) file (Specifically, the [group of the project](patches/build.gradle.kts#L1),
and the [About](patches/build.gradle.kts#L5-L11))
3. Update dependencies in the [libs.versions.toml](gradle/libs.versions.toml) file
4. [Create a pass-phrased GPG master key and subkey](https://mikeross.xyz/create-gpg-key-pair-with-subkeys/)
5. Add the following GitHub secrets:
   1. [GPG_PRIVATE_KEY](.github/workflows/release.yml#L52): The ASCII-armored GPG key
   2. [GPG_PASSPHRASE](.github/workflows/release.yml#L53): The passphrase for the GPG key
6. Add the following GitHub variables:
   1. [GPG_FINGERPRINT](.github/workflows/release.yml#L54): The fingerprint of the GPG key
7. Set up the [README.md](README.md) file[^1] (e.g, title, description, license, summary of the patches
that are included in the repository), the [issue templates](.github/ISSUE_TEMPLATE)[^2]  and the [contribution guidelines](CONTRIBUTING.md)[^3]

🎉 You are now ready to start creating patches!

[^1]: [Example README.md file](https://github.com/MorpheApp/morphe-patches/blob/main/README.md)
[^2]: [Example issue templates](https://github.com/MorpheApp/morphe-patches/tree/main/.github/ISSUE_TEMPLATE)
[^3]: [Example contribution guidelines](https://github.com/MorpheApp/morphe-patches/blob/main/CONTRIBUTING.md)

## 🔘 Optional steps

You can also add the following things to the repository:

- API documentation, if you want to publish your patches as a library

## 🧑‍💻 Usage

To develop and release Morphe Patches using this template, some things need to be considered:

- Development starts in feature branches. Once a feature branch is ready, it is squashed and merged into the `dev` branch
- The `dev` branch is merged into the `main` branch once it is ready for release
- Semantic versioning is used to version Morphe Patches. Morphe Patches have a public API for other patches to use
- Semantic commit messages are used for commits
- Commits on the `dev` branch and `main` branch are automatically released
via the [release.yml](.github/workflows/release.yml) workflow, which is also responsible for generating the changelog
and updating the version of Morphe Patches. It is triggered by pushing to the `dev` or `main` branch.
The workflow uses the `publish` task to publish the release of Morphe Patches
- The `buildAndroid` task is used to build Morphe Patches so that it can be used on Android.
The `publish` task depends on the `buildAndroid` task, so it will be run automatically when publishing a release.

## 📚 Everything else

### 📙 Contributing

Thank you for considering contributing to Morphe Patches template.  
You can find the contribution guidelines [here](CONTRIBUTING.md).

### 🛠️ Building

To build Morphe Patches template,
you can follow the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation).

## 📜 Licence

Morphe Patches are licensed under the [GNU GPL v3.0](https://www.gnu.org/licenses/gpl-3.0.html), with additional conditions under Section 7:

- **Name Restriction (7c):** The name **"Morphe"** may not be used for derivative works.  
  Derivatives must adopt a distinct identity unrelated to "Morphe."

See the [LICENSE](./LICENSE) file for full terms.
