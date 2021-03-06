/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.command;

import com.boydti.fawe.Fawe;
import com.boydti.fawe.config.BBC;
import com.boydti.fawe.config.Settings;
import com.boydti.fawe.database.DBHandler;
import com.boydti.fawe.database.RollbackDatabase;
import com.boydti.fawe.object.RunnableVal;
import com.boydti.fawe.object.changeset.DiskStorageHistory;
import com.boydti.fawe.util.MainUtil;
import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandPermissions;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.WorldVector;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.world.World;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;


import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Commands to undo, redo, and clear history.
 */
public class HistoryCommands {

    private final WorldEdit worldEdit;

    /**
     * Create a new instance.
     *
     * @param worldEdit reference to WorldEdit
     */
    public HistoryCommands(WorldEdit worldEdit) {
        checkNotNull(worldEdit);
        this.worldEdit = worldEdit;
    }

    @Command(
            aliases = { "/frb", "frb", "fawerollback", "/fawerollback" },
            usage = "<user> <radius> <time>",
            desc = "Undo edits within a radius",
            min = 3,
            max = 3
    )
    @CommandPermissions("worldedit.history.undo")
    public void faweRollback(final Player player, LocalSession session, final String user, int radius, String time) throws WorldEditException {
        if (!Settings.HISTORY.USE_DATABASE) {
            BBC.SETTING_DISABLE.send(player, "history.use-database");
            return;
        }
        UUID other = Fawe.imp().getUUID(user);
        if (other == null) {
            BBC.PLAYER_NOT_FOUND.send(player, user);
            return;
        }
        long timeDiff = MainUtil.timeToSec(time) * 1000;
        if (timeDiff == 0) {
            BBC.COMMAND_SYNTAX.send(player, "/frb " + user + " " + radius + " <time>");
            return;
        }
        radius = Math.max(Math.min(500, radius), 0);
        World world = player.getWorld();
        WorldVector origin = player.getPosition();
        Vector bot = origin.subtract(radius, radius, radius);
        Vector top = origin.add(radius, radius, radius);
        RollbackDatabase database = DBHandler.IMP.getDatabase(Fawe.imp().getWorldName(world));
        final AtomicInteger count = new AtomicInteger();
        database.getPotentialEdits(other, System.currentTimeMillis() - timeDiff, bot, top, new RunnableVal<DiskStorageHistory>() {
                @Override
                public void run(DiskStorageHistory edit) {
                    EditSession session = edit.toEditSession(null);
                    session.undo(session);
                    edit.deleteFiles();
                    BBC.ROLLBACK_ELEMENT.send(player, Fawe.imp().getWorldName(edit.getWorld()) + "/" + user + "-" + edit.getIndex());
                    count.incrementAndGet();
                }
            }, new Runnable() {
                @Override
                public void run() {
                    BBC.TOOL_INSPECT_INFO_FOOTER.send(player, count);
                }
            }, true
        );
    }

    @Command(
            aliases = { "/undo", "undo" },
            usage = "[times] [player]",
            desc = "Undoes the last action",
            min = 0,
            max = 2
    )
    @CommandPermissions("worldedit.history.undo")
    public void undo(Player player, LocalSession session, EditSession editSession, CommandContext args) throws WorldEditException {
        int times = Math.max(1, args.getInteger(0, 1));
        for (int i = 0; i < times; ++i) {
            EditSession undone;
            if (args.argsLength() < 2) {
                undone = session.undo(session.getBlockBag(player), player);
            } else {
                player.checkPermission("worldedit.history.undo.other");
                LocalSession sess = worldEdit.getSession(args.getString(1));
                if (sess == null) {
                    player.printError("Unable to find session for " + args.getString(1));
                    break;
                }
                undone = sess.undo(session.getBlockBag(player), player);
            }
            if (undone != null) {
                BBC.COMMAND_UNDO_SUCCESS.send(player);
                worldEdit.flushBlockBag(player, undone);
            } else {
                BBC.COMMAND_UNDO_FAIL.send(player);
                break;
            }
        }
    }

    @Command(
            aliases = { "/redo", "redo" },
            usage = "[times] [player]",
            desc = "Redoes the last action (from history)",
            min = 0,
            max = 2
    )
    @CommandPermissions("worldedit.history.redo")
    public void redo(Player player, LocalSession session, EditSession editSession, CommandContext args) throws WorldEditException {

        int times = Math.max(1, args.getInteger(0, 1));

        for (int i = 0; i < times; ++i) {
            EditSession redone;
            if (args.argsLength() < 2) {
                redone = session.redo(session.getBlockBag(player), player);
            } else {
                player.checkPermission("worldedit.history.redo.other");
                LocalSession sess = worldEdit.getSession(args.getString(1));
                if (sess == null) {
                    player.printError("Unable to find session for " + args.getString(1));
                    break;
                }
                redone = sess.redo(session.getBlockBag(player), player);
            }
            if (redone != null) {
                BBC.COMMAND_REDO_SUCCESS.send(player);
                worldEdit.flushBlockBag(player, redone);
            } else {
                BBC.COMMAND_REDO_FAIL.send(player);
            }
        }
    }

    @Command(
            aliases = { "/clearhistory", "clearhistory" },
            usage = "",
            desc = "Clear your history",
            min = 0,
            max = 0
    )
    @CommandPermissions("worldedit.history.clear")
    public void clearHistory(Player player, LocalSession session, EditSession editSession) throws WorldEditException {
        session.clearHistory();
        BBC.COMMAND_HISTORY_CLEAR.send(player);
    }

    public static Class<?> inject() {
        return HistoryCommands.class;
    }
}