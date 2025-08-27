**THIS MOD IS IN ALPHA, MANY FEATURES ARE CURRENTLY MISSING**

# Quantum

Quantum is a multi-world mod for Fabric, it allows players to create new worlds in both single player and multiplayer.

Because the mod is in alpha, it lacks many features like permissions configurations, etc. All commands require to be a server operator or enable 'Allow Cheats' in single-player.

**Keep in mind, worlds and portals must be created by a server operator**.

## How to use Quantum

Bellow you are going to learn how to use Quantum.

- All command arguments that are between **[]** are required.
- All command arguments that are between **()** are optional.

# How to create a new world

```
/qt createworld [worldName] (difficulty) (worldDimension)
```

Arguments:
- **worldName**: The name of your world.
- **difficulty**: The difficulty setting for your world (e.g., peaceful, easy, normal, hard).
- **worldDimension**: The dimension type for your world (e.g., overworld, nether, end, or a custom dimension from another mod).

Note:
- Once the world is created, it will automatically load when the server starts.
- The world’s configurations are stored in a file called quantum.dat located in the data directory of your world.

# How to delete a world

```
/qt deleteworld [worldName]
```

Arguments:
- **worldName**: The name of your world.

Note:
- This command will permanently delete the specified world, including all data and files associated with it.
- Make sure you have a backup if you want to keep any information from that world!

# How to change the spawn point of a world

```
/qt setworldspawn
```

This command sets the spawn point of the world to your current location, using your player’s position, yaw, and pitch (the direction you’re facing)

Note: 
- While Minecraft has a similar command, it is limited to the Overworld. This command works in any dimension, providing more flexibility

# How to teleprot using commands

```
/qt tp [worldName]
```

# How to teleport using signs

To set a destination, simply look at a sign and use the following command

```
/qt setdestination
```

Note:
- The back of the sign will automatically store the information about the destination world
- You can still freely edit the front of the sign to display any message or decoration you like

# How to create a portal

**IMPORTANT: If you want your portal to go to a new world, you have to use the command above to create a new world before creating a portal**

```
/qt createportal [portalFrameBlock] [portalIgniteItem] [portalDestinationWorldName]
```

Arguments:
- **portalFrameBlock** : The block type used to build the portal frame (only solid, full blocks are supported)
- **portalIgniteItem** : The item required to activate the portal (can be items, water, or lava)
- **portalActivationItem** : The item to use to activate the portal (items, water and lava are supported).

# How to delete a portal

```
/qt deleteportal [portalFrameBlock]
```

Arguments:
- **portalFrameBlock** : The block type used to build the portal frame.

Note: 
- After deleting a portal, it’s recommended to restart the server for the changes to fully take effect
- Deleted portals will not be physically destroyed. Instead, they will simply stop functioning

# Whats next ?

Thoses features are already planned: 
- Custom portal color

# A last word

This mod is mainly created for my personal use, i may or may not update it regulary. I'll still try to maintains it updated to the last minecraft version.

# Thanks

[CustomPortalAPI](https://github.com/kyrptonaught/customportalapi) : I use this lib for creating custom portals.

[Fantasy](https://github.com/NucleoidMC/fantasy) : I use this lib for creating worlds at runtime.
