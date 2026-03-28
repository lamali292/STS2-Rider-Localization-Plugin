## Installation

1. Download the latest `.zip` from the [Releases](../../releases) page
2. Open Rider and go to **File → Settings → Plugins**
3. Click the **⚙️** icon and select **Install Plugin from Disk...**
4. Select the downloaded `.zip` file and restart the IDE


## Configuration

Add a `cardloc-presets.json` file to your project root.

| Field | Description |
|-------|-------------|
| `projectId` | Your mod ID — the prefix that BaseLib creates (e.g. `Downfall`) |
| `localizationBase` | Path to your localization files (e.g. `Downfall/localization/eng`) |
| `markers` | Your custom classes for each preset type |

## Example -- cardloc-presets.json

It defines which classes the plugin recognises, as well as which localisation fields map to which JSON files.

For example, if you have a custom card class, you need to add it there.

You can also add relics or potions.

<details>
<summary>cardloc-presets.json</summary>
  
```json
{
  "localizationBase": "Downfall/localization/eng",
  "projectId": "Downfall",
  "presets": [
    {
      "id": "card",
      "markers": [
        "CustomCardModel"
      ],
      "fields": [
        {
          "name": "title",
          "file": "cards.json",
          "optional": false
        },
        {
          "name": "description",
          "file": "cards.json",
          "optional": false
        },
        {
          "name": "selectionScreenPrompt",
          "file": "cards.json",
          "optional": true
        }
      ]
    },
    {
      "id": "power",
      "markers": [
        "CustomPowerModel"
      ],
      "fields": [
        {
          "name": "title",
          "file": "powers.json",
          "optional": false
        },
        {
          "name": "description",
          "file": "powers.json",
          "optional": false
        },
        {
          "name": "smartDescription",
          "file": "powers.json",
          "optional": true
        }
      ]
    },
    {
      "id": "character",
      "markers": [
        "CustomCharacterModel"
      ],
      "fields": [
        {
          "name": "title",
          "file": "characters.json",
          "optional": false
        },
        {
          "name": "titleObject",
          "file": "characters.json",
          "optional": false
        },
        {
          "name": "pronounSubject",
          "file": "characters.json",
          "optional": false
        },
        {
          "name": "pronounPossessive",
          "file": "characters.json",
          "optional": false
        },
        {
          "name": "pronounObject",
          "file": "characters.json",
          "optional": false
        },
        {
          "name": "possessiveAdjective",
          "file": "characters.json",
          "optional": false
        },
        {
          "name": "goldMonologue",
          "file": "characters.json",
          "optional": false
        },
        {
          "name": "eventDeathPrevention",
          "file": "characters.json",
          "optional": false
        },
        {
          "name": "description",
          "file": "characters.json",
          "optional": false
        },
        {
          "name": "cardsModifierTitle",
          "file": "characters.json",
          "optional": false
        },
        {
          "name": "cardsModifierDescription",
          "file": "characters.json",
          "optional": false
        },
        {
          "name": "banter.dead.endTurnPing",
          "file": "characters.json",
          "optional": false
        },
        {
          "name": "banter.alive.endTurnPing",
          "file": "characters.json",
          "optional": false
        },
        {
          "name": "aromaPrinciple",
          "file": "characters.json",
          "optional": false
        }
      ]
    }
  ]
}
```
  
</details>
