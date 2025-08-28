**THIS MOD IS IN ALPHA, MANY FEATURES ARE CURRENTLY MISSING**

# PulsarCore

PulsarCore is a multi-world mod for Fabric, it allows players to create new worlds in both single player and multiplayer. This mod includes advanced world border management features across multiple dimensions.

Because the mod is in alpha, it lacks many features like permissions configurations, etc. All commands require to be a server operator or enable 'Allow Cheats' in single-player.

**Keep in mind, worlds and portals must be created by a server operator**.

## How to use PulsarCore

Below you are going to learn how to use PulsarCore.

- All command arguments that are between **[]** are required.
- All command arguments that are between **()** are optional.

# How to create a new world

```
/pulsarcore createworld [worldName] (difficulty) (worldDimension)
```

Arguments:
- **worldName**: The name of your world.
- **difficulty**: The difficulty setting for your world (e.g., peaceful, easy, normal, hard).
- **worldDimension**: The dimension type for your world (e.g., overworld, nether, end, or a custom dimension from another mod).

Note:
- Once the world is created, it will automatically load when the server starts.
- The world's configurations are stored in a file called pulsarcore.dat located in the data directory of your world.
- PulsarCore includes enhanced world border management for multi-dimensional worlds.

# How to delete a world

```
/pulsarcore deleteworld [worldName]
```

Arguments:
- **worldName**: The name of your world.

Note:
- This command will permanently delete the specified world, including all data and files associated with it.
- Make sure you have a backup if you want to keep any information from that world!

# How to change the spawn point of a world

```
/pulsarcore setworldspawn
```

This command sets the spawn point of the world to your current location, using your player's position, yaw, and pitch (the direction you're facing)

Note: 
- While Minecraft has a similar command, it is limited to the Overworld. This command works in any dimension, providing more flexibility
- PulsarCore automatically manages world borders when setting spawn points in different dimensions

# How to teleport using commands

```
/pulsarcore tp [worldName]
```

# How to teleport using signs

To set a destination, simply look at a sign and use the following command

```
/pulsarcore setdestination
```

Note:
- The back of the sign will automatically store the information about the destination world
- You can still freely edit the front of the sign to display any message or decoration you like
- World borders are automatically synchronized when using sign teleportation

# How to create a portal

**IMPORTANT: If you want your portal to go to a new world, you have to use the command above to create a new world before creating a portal**

```
/pulsarcore createportal [portalFrameBlock] [portalIgniteItem] [portalDestinationWorldName]
```

Arguments:
- **portalFrameBlock** : The block type used to build the portal frame (only solid, full blocks are supported)
- **portalIgniteItem** : The item required to activate the portal (can be items, water, or lava)
- **portalDestinationWorldName** : The destination world name for the portal

# How to delete a portal

```
/pulsarcore deleteportal [portalFrameBlock]
```

Arguments:
- **portalFrameBlock** : The block type used to build the portal frame.

Note: 
- After deleting a portal, it's recommended to restart the server for the changes to fully take effect
- Deleted portals will not be physically destroyed. Instead, they will simply stop functioning

# WorldBorderFix Integration

PulsarCore comes with built-in **Multi World Borders** compatibility, providing enhanced world border functionality:

## Features
- **Per-Dimension Borders**: Each dimension (Overworld, Nether, End, and custom worlds) can have independent world borders
- **Dynamic World Detection**: Automatically detects and manages borders for PulsarCore-created worlds
- **Persistent Settings**: World border configurations are saved and restored across server restarts
- **Full Compatibility**: Works seamlessly with all PulsarCore world management features

## How It Works
- When you create a new world with PulsarCore, it automatically gets its own independent world border
- World borders are managed per-dimension, allowing different sizes and settings for each world
- All vanilla world border commands work normally, but now affect only the current dimension
- Border settings persist across server restarts and world reloads

## Credits
- **Original WorldBorderFix**: Created by [Potatoboy9999](https://github.com/PotatoPresident/worldborderfixer)
- **PulsarCore Integration**: Enhanced by Gabrieli2806 for seamless compatibility
- **More Info**: Visit the [official WorldBorderFix page](https://www.curseforge.com/minecraft/mc-mods/world-border-fix)

# Current Features

PulsarCore includes the following core features:
- Multi-dimensional world creation and management
- Advanced world border synchronization across dimensions
- Cross-dimensional teleportation via commands and signs
- Custom portal creation with configurable blocks and activation items
- Enhanced world spawn management for all dimensions
- **Integrated WorldBorderFix**: Full compatibility with Multi World Borders mod for per-dimension world border management

# Planned Features

The following features are planned for future updates:
- Custom portal colors and effects
- Permissions system and configuration options
- Advanced world border management tools
- Custom dimension support enhancements

# Requirements

- **Minecraft**: 1.21
- **Fabric Loader**: 0.15.11 or later
- **Java**: 21 or later  
- **Fabric API**: Latest version
- **Fabric Language Kotlin**: 1.11.0+kotlin.2.0.0 or later

# Development

This mod is based on the original Quantum mod by Unreal852, with additional features and improvements by Gabrieli2806. The mod focuses on advanced world border management and multi-dimensional functionality.

# Credits

- **Unreal852**: Original Quantum mod author
- **Gabrieli2806**: Additional features and PulsarCore development
- **Potatoboy9999**: Original WorldBorderFix/Multi World Borders mod
- [CustomPortalAPI](https://github.com/kyrptonaught/customportalapi): Custom portal functionality
- [Fantasy](https://github.com/NucleoidMC/fantasy): Runtime world creation
- [WorldBorderFix](https://github.com/PotatoPresident/worldborderfixer): Per-dimension world border management
