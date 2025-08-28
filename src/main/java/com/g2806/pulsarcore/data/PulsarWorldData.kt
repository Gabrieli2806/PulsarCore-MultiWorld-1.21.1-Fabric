package com.g2806.pulsarcore.data

import net.minecraft.nbt.NbtCompound
import net.minecraft.util.Identifier
import net.minecraft.world.Difficulty
import xyz.nucleoid.fantasy.RuntimeWorldConfig

class PulsarWorldData(worldId: Identifier, dimensionId: Identifier, runtimeWorldConfig: RuntimeWorldConfig) {
    var worldId: Identifier = worldId
        private set
    var dimensionId: Identifier = dimensionId
        private set
    var runtimeWorldConfig: RuntimeWorldConfig = runtimeWorldConfig
        private set

    // Store GameRules settings
    private val gameRulesData: MutableMap<String, String> = mutableMapOf()

    // Store WorldBorder settings
    private val worldBorderData: MutableMap<String, String> = mutableMapOf()

    fun setGameRule(gameRuleName: String, value: String) {
        gameRulesData[gameRuleName] = value
    }

    fun getGameRule(gameRuleName: String): String? {
        return gameRulesData[gameRuleName]
    }

    fun getAllGameRules(): Map<String, String> {
        return gameRulesData.toMap()
    }

    fun setWorldBorderProperty(property: String, value: String) {
        worldBorderData[property] = value
    }

    fun getWorldBorderProperty(property: String): String? {
        return worldBorderData[property]
    }

    fun getAllWorldBorderProperties(): Map<String, String> {
        return worldBorderData.toMap()
    }

    fun writeToNbt(nbt: NbtCompound) {
        nbt.putString("worldId", worldId.toString())
        nbt.putString("dimensionId", dimensionId.toString())
        nbt.putLong("seed", runtimeWorldConfig.seed)
        nbt.putInt("difficulty", runtimeWorldConfig.difficulty.id)
        nbt.putBoolean("tick", runtimeWorldConfig.shouldTickTime())

        // Save GameRules
        val gameRulesNbt = NbtCompound()
        gameRulesData.forEach { (key, value) ->
            gameRulesNbt.putString(key, value)
        }
        nbt.put("gameRules", gameRulesNbt)

        // Save WorldBorder
        val worldBorderNbt = NbtCompound()
        worldBorderData.forEach { (key, value) ->
            worldBorderNbt.putString(key, value)
        }
        nbt.put("worldBorder", worldBorderNbt)
    }

    companion object {
        fun fromNbt(nbt: NbtCompound): PulsarWorldData {
            val worldId = Identifier.of(nbt.getString("worldId"))
            val dimensionId = Identifier.of(nbt.getString("dimensionId"))
            val seed = nbt.getLong("seed")
            val difficulty = Difficulty.byId(nbt.getInt("difficulty"))
            val shouldTick = nbt.getBoolean("tick")

            val worldData = PulsarWorldData(
                worldId,
                dimensionId,
                RuntimeWorldConfig()
                    .setSeed(seed)
                    .setDifficulty(difficulty)
                    .setShouldTickTime(shouldTick)
            )

            // Load GameRules
            if (nbt.contains("gameRules")) {
                val gameRulesNbt = nbt.getCompound("gameRules")
                gameRulesNbt.keys.forEach { key ->
                    worldData.setGameRule(key, gameRulesNbt.getString(key))
                }
            }

            // Load WorldBorder
            if (nbt.contains("worldBorder")) {
                val worldBorderNbt = nbt.getCompound("worldBorder")
                worldBorderNbt.keys.forEach { key ->
                    worldData.setWorldBorderProperty(key, worldBorderNbt.getString(key))
                }
            }

            return worldData
        }
    }
}