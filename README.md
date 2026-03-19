# 👋🧩 Morphe Patches template

Template repository for Morphe Patches.

&nbsp;
## ❓ About

This is a template to create a new Morphe Patches repository.  
The repository can have multiple patches, and patches from other repositories can be used together.

For an example repository, see [Morphe Patches](https://github.com/MorpheApp/morphe-patches).

Morphe Patches template is based off the prior work of [ReVanced](https://github.com/ReVanced/revanced-patches-template).
All modifications made by Morphe, along with their dates, can be found in the Git history.


## 🚀 Get started

To start using this template, follow these steps:

1. [Create a new repository using this template](https://github.com/new?template_name=morphe-patches-template&template_owner=MorpheApp)
2. Set up the [build.gradle.kts](patches/build.gradle.kts) file (Specifically, the [group of the project](patches/build.gradle.kts#L1),
and the [About](patches/build.gradle.kts#L5-L11))
3. Set up the [README.md](README.md) file[^1] (e.g, title, description, license, summary of the patches
that are included in the repository), the [issue templates](.github/ISSUE_TEMPLATE)[^2]  and the [contribution guidelines](CONTRIBUTING.md)[^3]
4. Choose a name for your patches project. Keep in mind you must use a unique name that does not imply or suggest authorship by the Morphe open source project.
   See the [NOTICE](NOTICE) for details.
5. (Optional): Add `patches-bundle.png` to the project if you want a custom icon to show in
   Morphe Manager instead of your GitHub profile avatar.

🎉 You are now ready to start creating patches!

## 🧑‍💻 Usage

To develop and release Morphe Patches using this template, some things need to be considered:

- Development starts in feature branches. Once a feature branch is ready, it is squashed and merged into the `dev` branch
- The `dev` branch is merged into the `main` branch once it is ready for release
- Semantic versioning is used to version Morphe Patches.
- [Semantic commit](https://kapeli.com/cheat_sheets/Semantic_Commits.docset/Contents/Resources/Documents/index) messages are used for commits
- Commits on the `dev` branch and `main` branch are automatically released
via the [release.yml](.github/workflows/release.yml) workflow, which is also responsible for generating the changelog
and updating the version of Morphe Patches. It is triggered by pushing to the `dev` or `main` branch.
The workflow uses the `publish` task to publish the release of Morphe Patches
- The `buildAndroid` task is used to build Morphe Patches so that it can be used on Android.
The `publish` task depends on the `buildAndroid` task, so it will be run automatically when publishing a release.

## 📚 Everything else

Optionally you can include a button/link in this readme that users can click to add your 
patches to Morphe (update the links below after creating your new patches repo):

#### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=xyz-user/xyz-patches

Or manually add this repository url as a patch source in Morphe: https://github.com/xyz-user/xyz-patches

### 📙 Contributing

Thank you for considering contributing to Morphe Patches template.  
You can find the contribution guidelines [here](CONTRIBUTING.md).

### 🛠️ Building

To build Morphe Patches template,
you can follow the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation).

## 📜 License

Morphe Patches are licensed under the [GNU General Public License v3.0](LICENSE), with additional conditions under GPLv3 Section 7:

- **Name Restriction (7c):** The name **"Morphe"** may not be used for derivative works.  
  Derivatives must adopt a distinct identity unrelated to "Morphe."

See the [LICENSE](LICENSE) file for the full GPLv3 terms and the [NOTICE](NOTICE) file for full conditions of GPLv3 Section 7
