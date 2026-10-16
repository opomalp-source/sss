# Bundled runtime dependency: Infiniverse

Dragon Block Zenith v0.140.0 bundles **Infiniverse 1.20.1-1.0.0.5** by Commoble under the **MIT license**.

- Official project: https://github.com/Commoble/infiniverse/tree/1.20.1
- Official release metadata/download: https://modrinth.com/mod/infiniverse/version/4ddkRT11
- License: https://github.com/Commoble/infiniverse/blob/1.20.1/LICENSE
- Original binary SHA-256: `0a9e3ad0bdcee18959c1ecc68e76eca9cf25f0ab02565219db5e35ceaced3c2a`

The unmodified original binary is checked into `libs/maven/maven/modrinth/infiniverse/1.0.0.5/`, with a local
Maven POM so offline builds remain reproducible. Gradle remaps it for development and bundles it through
Jar-in-Jar for production. Its MIT notice is alongside the binary and included at
`META-INF/licenses/INFINIVERSE-LICENSE.txt` in the DBZ jar. No separate installation is required.

The library manages runtime registration/client dimension lists. DBZ owns the planet cap, async landing,
save/close hooks, independent borders and bed-reference preservation. The original library assets/code are
licensed dependency material; DBZ's new suit icons/armor and galaxy visual art are original code-generated assets.
