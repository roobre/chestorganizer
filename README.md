# <img alt="📦" src="https://raw.githubusercontent.com/roobre/chestorganizer/master/etc/assets/icon.png" height="30" /> ChestOrganizer

[![Build status](https://api.travis-ci.org/roobre/chestorganizer.svg?branch=master)](https://travis-ci.org/roobre/chestorganizer)

ChestOrganizer is a plugin for bukkit and bukkit-compatible minecraft servers that allows players to create special chests, which distribute items to other nearby containers automatically.

## Usage

1. Place a chest on top of a redstone block (not ore). This chest is now an Organizer.
2. Put an item inside the chest.

If any nearby (in a 10x10 area centered at the organizer chest) container already contain an instance of the item, the item you just put on the organizer chest will be moved to it.

Only (single) chests can act as organizers, but any container can be a target if if meets the requirements above.

## Instalation

Download latest release from the *Releases* tab and place it into the `plugins` directory of your server.

## Supported versions

This was tested (although not extensively) in the following configurations:

| Server | Version | ? |
|--------|---------|---|
| Spigot | 1.14.3  | ✅ |

Other bukkit-compliant servers and minecraft versions will probably work too, since the plugin does not use any exotic API.
