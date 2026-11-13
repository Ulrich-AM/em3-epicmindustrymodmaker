package em3.animation;

import java.util.ArrayList;
import java.util.List;

/** Tracks driven by one PartParams channel: warmup, recoil, charge, or reload. */
public final class ChannelAnimation {
    public String channel = "warmup";
    public final List<AnimTrack> tracks = new ArrayList<>();

    public ChannelAnimation() {
    }

    public ChannelAnimation(String channel) {
        this.channel = channel;
    }

    public AnimTrack track(String property) {
        for (AnimTrack track : tracks) {
            if (property.equals(track.property)) {
                return track;
            }
        }
        return null;
    }

    public AnimTrack trackOrCreate(String property) {
        AnimTrack found = track(property);
        if (found != null) {
            return found;
        }
        AnimTrack created = new AnimTrack(property);
        tracks.add(created);
        return created;
    }

    public ChannelAnimation copy() {
        ChannelAnimation c = new ChannelAnimation(channel);
        for (AnimTrack track : tracks) {
            c.tracks.add(track.copy());
        }
        return c;
    }
}
