package hudson.scm;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * {@link SCMRevisionState} for {@link SubversionSCM}. {@link Serializable} since we compute
 * this remote.
 */
public final class SVNRevisionState extends SCMRevisionState implements Serializable {
    /**
     * All the remote locations that we checked out. This includes those that are specified
     * explicitly via {@link SubversionSCM#getLocations()} as well as those that
     * are implicitly pulled in via svn:externals, but it excludes those locations that
     * are added via svn:externals in a way that fixes revisions.
     */
    final Map<String,Long> revisions;

    SVNRevisionState() {
        this.revisions = new LinkedHashMap<String, Long>();
    }

    SVNRevisionState(final Map<String, Long> revisions) {
        this.revisions = revisions;
    }

    public long getRevision(final String location) {
        return this.revisions.get(location);
    }

    @Override public String toString() {
        return "SVNRevisionState" + this.revisions;
    }

    private static final long serialVersionUID = 1L;

    /**
     * Adds revisions from given map that are not already contained.
     */
    public void addRevisions(final Map<String, Long> revisions, final boolean forceUpdate) {
        for (final Entry<String, Long> revision : revisions.entrySet()) {
            if (forceUpdate || !this.revisions.containsKey(revision.getKey())) {
                this.revisions.put(revision.getKey(), revision.getValue());
            }
        }
    }
}
