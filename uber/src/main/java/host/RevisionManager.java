package host;

import lombok.Getter;

public class RevisionManager {
    private static RevisionManager INSTANCE;
    @Getter
    private long revision = 0;

    public static synchronized RevisionManager getInstance() {
        if(INSTANCE == null) {
            INSTANCE = new RevisionManager();
        }
        return INSTANCE;
    }

    public long updateAndGet() {
        revision++;
        return revision;
    }

    public synchronized void setRevision(long newRevision) {
        if (newRevision > revision) {
            revision = newRevision;
        }
    }
}
