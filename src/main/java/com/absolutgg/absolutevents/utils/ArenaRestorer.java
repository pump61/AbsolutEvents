package com.absolutgg.absolutevents.utils;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.FileInputStream;

public class ArenaRestorer {

    public static void restore(AbsolutEventsPlugin plugin, ConfigurationSection arenaConfig) {
        try {
            String worldName = arenaConfig.getString("world");
            World bukkitWorld = Bukkit.getWorld(worldName);

            if (bukkitWorld == null) {
                plugin.getLogger().warning("Mundo da arena não encontrado: " + worldName);
                return;
            }

            int x = arenaConfig.getInt("restore-origin.x");
            int y = arenaConfig.getInt("restore-origin.y");
            int z = arenaConfig.getInt("restore-origin.z");

            String schematicPath = arenaConfig.getString("schematic");
            File file = new File(plugin.getDataFolder(), schematicPath);

            if (!file.exists()) {
                plugin.getLogger().warning("Schematic não encontrada: " + file.getAbsolutePath());
                return;
            }

            plugin.getLogger().info("Restaurando arena...");
            plugin.getLogger().info("Mundo: " + worldName);
            plugin.getLogger().info("Local: " + x + ", " + y + ", " + z);
            plugin.getLogger().info("Arquivo: " + file.getAbsolutePath());

            ClipboardFormat format = ClipboardFormats.findByFile(file);
            if (format == null) {
                plugin.getLogger().warning("Formato de schematic inválido!");
                return;
            }

            try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
                Clipboard clipboard = reader.read();

                try (EditSession editSession = WorldEdit.getInstance()
                        .newEditSession(BukkitAdapter.adapt(bukkitWorld))) {

                    Operation operation = new ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(BlockVector3.at(x, y, z))
                            .ignoreAirBlocks(false)
                            .build();

                    Operations.complete(operation);

                    // 🔥 ESSENCIAL
                    editSession.flushSession();
                }
            }

            plugin.getLogger().info("Arena restaurada com sucesso!");

        } catch (Exception e) {
            plugin.getLogger().severe("Erro ao restaurar arena:");
            e.printStackTrace();
        }
    }
}