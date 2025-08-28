package com.g2806.pulsarcore.suggestion

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.block.BlockWithEntity
import net.minecraft.block.PlantBlock
import net.minecraft.item.BlockItem
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.BlockTags
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.world.Difficulty
import java.util.concurrent.CompletableFuture

class BlocksSuggestionProvider : SuggestionProvider<ServerCommandSource> {
    override fun getSuggestions(context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        for (block in Registries.BLOCK) {
            val blockState = block.defaultState

            if (block is BlockWithEntity || block is PlantBlock) continue

            if (blockState.isIn(BlockTags.AIR) || blockState.isIn(BlockTags.DOORS) || 
                blockState.isIn(BlockTags.BUTTONS) || blockState.isIn(BlockTags.ALL_SIGNS) || 
                blockState.isIn(BlockTags.SLABS) || blockState.isIn(BlockTags.STAIRS) ||
                blockState.isIn(BlockTags.BEDS) || blockState.isIn(BlockTags.BANNERS) || 
                blockState.isIn(BlockTags.RAILS) || blockState.isIn(BlockTags.TRAPDOORS) ||
                blockState.isIn(BlockTags.PRESSURE_PLATES) || blockState.isIn(BlockTags.FENCES) || 
                blockState.isIn(BlockTags.FENCE_GATES) || blockState.isIn(BlockTags.SAPLINGS) || 
                blockState.isIn(BlockTags.WOOL_CARPETS)) continue

            builder.suggest(blockState.registryEntry.idAsString)
        }
        return builder.buildFuture()
    }
}

class DifficultySuggestionProvider : SuggestionProvider<ServerCommandSource> {
    override fun getSuggestions(context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        for (difficulty in Difficulty.entries) {
            builder.suggest(difficulty.name)
        }
        return builder.buildFuture()
    }
}

class ItemsSuggestionProvider : SuggestionProvider<ServerCommandSource> {
    override fun getSuggestions(context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        for (item in Registries.ITEM) {
            if (item != Items.AIR && item !is BlockItem) {
                builder.suggest(item.toString())
            }
        }
        return builder.buildFuture()
    }
}

class WorldsDimensionSuggestionProvider : SuggestionProvider<ServerCommandSource> {
    override fun getSuggestions(context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        for (world in context.source.worldKeys) {
            builder.suggest(world.value.toString())
        }
        return builder.buildFuture()
    }
}