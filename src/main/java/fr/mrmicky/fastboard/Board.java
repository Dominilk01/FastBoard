package fr.mrmicky.fastboard;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

// TOP INTERFACE FOR EVERYTHING
// didn't added all the thread safety part
public interface Board<T> {

    // code that normal FastBoard user write
    public class NormalUserCode {
        static {
            Player plr = null;
            // Used ComponentBoard / StringBoard to easily separate but will merge back names
            ComponentBoard board = new ComponentBoard(plr);
            board.updateText(1, Component.text("using normally"));
            board.updateFormat(1, Component.text("the library"));
            board.updateLine(1, Component.text("without"), Component.text("without apparent change"));
            // can use normally and user don't need to care the underlying
        }
    }

    // need to add other user facing functions...

    @Deprecated
    void updateScore(int line, T score);

    @Deprecated
    void updateLine(int line, T score);

    /**
     * function for users<br>
     * and that will be overridden by version specific code
     */
    void updateText(int position, T text);

    /**
     * function for users<br>
     * and that will be overridden by version specific code
     */
    void updateFormat(int position, T text);

    /**
     * function for users<br>
     * and that will be overridden by version specific code
     */
    void updateLine(int position, T text, T format);

    //\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\
    //\\//\\//\\//\\//\\//\\//\\//\ Version Classes //\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\
    //\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\

    // THE PARENT CLASS FOR EACH VERSION
    public abstract class VersionBoard<T> implements Board<T> {
        protected final List<T> text = new ArrayList<>();
        protected final List<T> formats = new ArrayList<>();
        protected final Formatter<T> formatter;
        protected final Player player;

        // can be top class
        private static final MethodHandle PLAYER_CONNECTION;
        private static final MethodHandle SEND_PACKET;
        private static final MethodHandle PLAYER_GET_HANDLE;

        // after 1.20.3 entity name is not showed (if Component part in packet is set),
        // but ¯\_(ツ)_/¯ can still use it
        protected static final String[] COLOR_CODES = Arrays.stream(ChatColor.values())
                .map(Object::toString)
                .toArray(String[]::new);

        static {
            PLAYER_CONNECTION = null;
            SEND_PACKET = null;
            PLAYER_GET_HANDLE = null;
        }

        protected VersionBoard(Formatter<T> formatter, Player plr) {
            this.formatter = formatter;
            this.player = plr;
        }

        protected T getTextByPosition(int p) {
            return text.get(p);
        }

        protected T getFormatByPosition(int p) {
            return formats.get(p);
        }

        @Contract(pure = true)
        protected Object toMinecraftComponent(T value) {
            return formatter.toMinecraftComponent(value);
        }

        protected void sendPacket(Object packet) throws Throwable {
            if (this.player.isOnline()) {
                Object entityPlayer = PLAYER_GET_HANDLE.invoke(this.player);
                Object playerConnection = PLAYER_CONNECTION.invoke(entityPlayer);
                SEND_PACKET.invoke(playerConnection, packet);
            }
        }

        public enum ObjectiveMode {
            CREATE, REMOVE, UPDATE
        }

        public enum TeamMode {
            CREATE, REMOVE, UPDATE, ADD_PLAYERS, REMOVE_PLAYERS
        }

        public enum ScoreboardAction {
            CHANGE, REMOVE
        }
    }

    public class Board1_20_3<T> extends VersionBoard<T> {

        // here can be version specific reflections
        private static final Object BLANK_NUMBER_FORMAT;

        public Board1_20_3(Formatter<T> formatter, Player plr) {
            super(formatter, plr);
        }

        static {
            BLANK_NUMBER_FORMAT = null;
        }

        @Override
        @Deprecated
        public void updateScore(int line, T score) {
            // shouldn't be routed here
        }

        @Override
        @Deprecated
        public void updateLine(int line, T score) {
            // shouldn't be routed here
        }

        @Override
        public void updateText(int position, T text) {
            this.text.set(position, text);
            internalTeamSend(position);
        }

        @Override
        public void updateFormat(int position, T text) {
            this.formats.set(position, text);
            internalTeamSend(position);
        }

        @Override
        public void updateLine(int position, T text, T format) {
            this.text.set(position, format);
            this.formats.set(position, text);
            internalTeamSend(position);
        }

        protected void internalTeamSend(int line) {
            T format = getTextByPosition(line);
            T text = getFormatByPosition(line);
            Object mcText = toMinecraftComponent(format);
            Object mcFormat = toMinecraftComponent(text);
            // updated text or format sending packet
            Object packet = null;
            try {
                sendPacket(packet);
            } catch (Throwable e) {
                System.out.println("couldn't send packet");
            }
        }
    }

    public class Board1_17<T> extends VersionBoard<T> {
        // here can be version specific reflections
        private static final Object PACKET_SB_TEAM;

        public Board1_17(Formatter<T> formatter, Player plr) {
            super(formatter, plr);
        }

        static {
            PACKET_SB_TEAM = null;
        }

        @Override
        @Deprecated
        public void updateScore(int line, T score) {
            // shouldn't be routed here
        }

        @Override
        @Deprecated
        public void updateLine(int line, T score) {
            // shouldn't be routed here
        }

        @Override
        public void updateLine(int position, T text, T format) {
            updateFormat(position, format);
            updateText(position, text);
        }

        @Override
        public void updateText(int position, T text) {
            this.text.set(position, text);
            // send team packet
            List<T> content = formatter.compatibilityFormat(position);
            try {
                Object team = null;
                setComponentField(team, null, 1); // Display name
                setComponentField(team, content.get(0), 2); // Prefix
                setComponentField(team, content.get(1), 3); // Suffix
                sendPacket(team);
            } catch (Throwable e) {
                System.out.println("couldn't send packet@");
            }
        }

        @Override
        public void updateFormat(int position, T text) {
            this.formats.set(position, text);
            // send score packet
            List<T> content = formatter.compatibilityFormat(position);
            try {
                String objName = COLOR_CODES[position];
                Object enumAction = action == FastBoardBase.ScoreboardAction.REMOVE
                        ? ENUM_SB_ACTION_REMOVE : ENUM_SB_ACTION_CHANGE;

                sendPacket(PACKET_SB_SET_SCORE.invoke(enumAction, this.id, objName, score));
            } catch (Throwable e) {
                System.out.println("couldn't send packet@");
            }
        }
        // I think it should be versioned specific function,
        // as 1.20.3 don't use it, so variable `PACKETS` shouldn't exist for 1.20.3

        private void setField(Object object, Class<?> fieldType, Object value) throws ReflectiveOperationException {
            setField(object, fieldType, value, 0);
        }

        private void setField(Object packet, Class<?> fieldType, Object value, int count)
                throws ReflectiveOperationException {
            int i = 0;
            for (Field field : PACKETS.get(packet.getClass())) {
                if (field.getType() == fieldType && count == i++) {
                    field.set(packet, value);
                }
            }
        }

        private void setComponentField(Object packet, T value, int count) throws Throwable {
            int i = 0;
            for (Field field : PACKETS.get(packet.getClass())) {
                if ((field.getType() == String.class || field.getType() == CHAT_COMPONENT_CLASS) && count == i++) {
                    field.set(packet, toMinecraftComponent(value));
                }
            }
        }
    }

    //\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\
    //\\//\\//\\//\\//\\//\\//\\//\ Format Classes \//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\
    //\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\//\\

    // PARENT CLASS FOR ADDING FUNCTION IN STRING/COMPONENT CLASS
    public abstract class Formatter<T> implements Board<T> {
        @Contract(pure = true)
        @NonNull
        protected abstract Object toMinecraftComponent(T value);

        @Contract(pure = true)
        @Unmodifiable
        @NonNull
        protected abstract List<T> compatibilityFormat(int line);
    }

    public class StringBoard extends Formatter<String> {
        private final VersionBoard<String> versionBase;

        public StringBoard(Player plr) {
            this.versionBase = VersionBaseFactory.create(this, plr);
        }

        @Override
        @Contract(pure = true)
        @NonNull
        @Unmodifiable
        protected final Object toMinecraftComponent(String value) {
            // will replace with reflection, it's just for demo
            return util.CraftChatMessage.fromString(value);
        }

        @Override
        @Contract(pure = true)
        @NonNull
        @Unmodifiable
        protected final List<String> compatibilityFormat(int line) {
            String prefix = "PREFIX";
            String suffix = "AND SUFFIX STUFF";
            return Arrays.asList(prefix, suffix);
        }

        @Override
        @Deprecated
        public void updateScore(int line, String score) {
            updateFormat(line, score);
        }

        @Override
        @Deprecated
        public void updateLine(int line, String score) {
            updateText(line, score);
        }

        @Override
        public void updateText(int position, String text) {
            versionBase.updateText(position, text);
        }

        @Override
        public void updateFormat(int position, String text) {
            versionBase.updateFormat(position, text);
        }

        @Override
        public void updateLine(int position, String text, String format) {
            versionBase.updateLine(position, text, format);
        }
    }

    public class ComponentBoard extends Formatter<Component> {
        private final VersionBoard<Component> versionBase;

        public ComponentBoard(Player plr) {
            this.versionBase = VersionBaseFactory.create(this, plr);
        }

        @Override
        @Contract(pure = true)
        @NotNull
        @Unmodifiable
        protected final Object toMinecraftComponent(Component value) {
            // will replace with reflection, it's just for demo
            return io.papermc.paper.adventure.PaperAdventure.asVanilla(value);
        }

        @Override
        @Contract(pure = true)
        @NotNull
        @Unmodifiable
        protected final List<Component> compatibilityFormat(int position) {
            Component text = versionBase.getTextByPosition(position);
            return Arrays.asList(text, null); // need to set 2nd element to prevent IndexOutOfBoundsException
        }

        // send the user called function to good version
        @Override
        @Deprecated
        public void updateScore(int line, Component score) {
            updateFormat(line, score);
        }

        @Override
        @Deprecated
        public void updateLine(int line, Component score) {
            updateText(line, score);
        }

        @Override
        public void updateText(int position, Component text) {
            versionBase.updateText(position, text);
        }

        @Override
        public void updateFormat(int position, Component text) {
            versionBase.updateFormat(position, text);
        }

        @Override
        public void updateLine(int position, Component text, Component format) {
            versionBase.updateLine(position, text, format);
        }
    }

    // made with claude (should be java 8 (1.7.10) compatible)
    final class VersionBaseFactory {
        private VersionBaseFactory() {
        }

        private static final BiFunction<Formatter<?>, Player, VersionBoard<?>> FACTORY = resolveFactory();

        private static BiFunction<Formatter<?>, Player, VersionBoard<?>> resolveFactory() {
            String version = Bukkit.getServer().getBukkitVersion();
            if (FastReflection.nmsOptionalClass("network.chat.numbers", "NumberFormat").isPresent()) {
                return Board1_20_3::new;
            } else if (FastReflection.isRepackaged()) {
                return Board1_17::new;
            }
            throw new UnsupportedOperationException("Unsupported server version: " + version);
        }

        @SuppressWarnings("unchecked")
        static <T> VersionBoard<T> create(Formatter<T> formatter, Player plr) {
            return (VersionBoard<T>) FACTORY.apply(formatter, plr);
        }
    }
}
