package app.onda.mobile;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Deterministic platform player; the real Media3 adapter and service remain under test. */
final class ControlledMedia3Player {
    final List<Player.Listener> listeners = new ArrayList<>();
    final Player player;
    boolean playing, requested, released;
    long position, duration = 120000;
    int prepareCalls, seekCalls, playCalls, state = Player.STATE_IDLE;
    MediaItem mediaItem;

    ControlledMedia3Player() {
        player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "addListener": listeners.add((Player.Listener) args[0]); return null;
                case "removeListener": listeners.remove(args[0]); return null;
                case "setMediaItem": mediaItem = (MediaItem) args[0]; return null;
                case "prepare": prepareCalls++; state(Player.STATE_BUFFERING); return null;
                case "play": requested = true; playing = true; playCalls++; changed(); return null;
                case "pause": requested = false; playing = false; changed(); return null;
                case "isPlaying": return playing;
                case "getPlayWhenReady": return requested;
                case "getDuration": return duration;
                case "getCurrentPosition": return position;
                case "getPlaybackState": return state;
                case "seekTo":
                    seekCalls++; position = ((Number) args[args.length - 1]).longValue();
                    state(Player.STATE_BUFFERING); state(Player.STATE_READY); return null;
                case "release": released = true; playing = false; return null;
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return "ControlledMedia3Player";
                default:
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == long.class) return 0L;
                    if (type == float.class) return 0F;
                    return null;
            }
        });
    }
    void state(int value) {
        state = value;
        if (value == Player.STATE_ENDED) playing = false;
        for (Player.Listener listener : new ArrayList<>(listeners)) listener.onPlaybackStateChanged(value);
    }
    void changed() { for (Player.Listener listener : new ArrayList<>(listeners)) listener.onIsPlayingChanged(playing); }
    void fail() {
        for (Player.Listener listener : new ArrayList<>(listeners))
            listener.onPlayerError(new PlaybackException("Injected decode error", null, PlaybackException.ERROR_CODE_DECODING_FAILED));
    }
}
