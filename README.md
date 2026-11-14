## Warning 
In rare cases, JSON localization files can be reset during autosave if the plugin hasn't fully loaded yet. 
Keep backups of your localization files. 
Not a priority to fix - this is a personal productivity tool and spending time maintaining it defeats its purpose.


## Installation

1. Download the latest `.zip` from the [Releases](../../releases) page
2. Open Rider and go to **File → Settings → Plugins**
3. Click the **⚙️** icon and select **Install Plugin from Disk...**
4. Select the downloaded `.zip` file and restart the IDE


## Configuration

Settings are stored per project in `.idea/cardloc.xml` (commit it to share them with your team).
Open a card class and use the settings button in the CardLoc tool window - or the "Configure" prompt
that appears on an unconfigured project - to set:

| Field | Description |
|-------|-------------|
| `Mod ID` | Your mod ID - the prefix that BaseLib creates (e.g. `Downfall`) |
| `Localization Base` | Path to your localization files (e.g. `Downfall/localization/eng`) |
| `Presets` | Which classes (markers) map to which localization fields. Sensible BaseLib defaults are built in |

### Migrating from `cardloc-presets.json` (deprecated)

Each `cardloc-presets.json` is imported once into the project settings the first time a class below its
folder is opened (one settings entry per folder, so multi-mod solutions keep separate configs).
After that the file is ignored and can be deleted.
