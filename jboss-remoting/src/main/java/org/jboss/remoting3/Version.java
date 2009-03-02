package org.jboss.remoting3;

/**
 * The version of Remoting.
 *
 * @apiviz.exclude
 */
public final class Version {

    private Version() {
    }

    /**
     * The version.
     */
    public static final String VERSION = "3.1.0.CR1";

    /**
     * Print the version to {@code System.out}.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        System.out.print(VERSION);
    }
}
