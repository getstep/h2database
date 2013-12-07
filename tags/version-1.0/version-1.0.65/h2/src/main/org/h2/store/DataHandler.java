/*
 * Copyright 2004-2007 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store;

import java.sql.SQLException;

import org.h2.value.Value;

/**
 * A data handler contains a number of callback methods.
 * The most important implementing class is a database.
 */
public interface DataHandler {

    /**
     * Check if text storage is used.
     *
     * @return if text storage is used.
     */
    boolean getTextStorage();

    /**
     * Get the database path.
     *
     * @return the database path
     */
    String getDatabasePath();

    /**
     * Open a file at the given location.
     *
     * @param name the file name
     * @param mode the mode
     * @param mustExist whether the file must already exist
     * @return the file
     */
    FileStore openFile(String name, String mode, boolean mustExist) throws SQLException;

    /**
     * Calculate the checksum for the byte array.
     *
     * @param data the byte array
     * @param start the starting offset
     * @param end the end offset
     * @return the checksum
     */
    int getChecksum(byte[] data, int start, int end);

    /**
     * Check if the simulated power failure occured.
     * This call will decrement the countdown.
     *
     * @throws SQLException if the simulated power failure occured
     */
    void checkPowerOff() throws SQLException;

    /**
     * Check if writing is allowed.
     *
     * @throws SQLException if it is not allowed
     */
    void checkWritingAllowed() throws SQLException;

    /**
     * Free up disk space if possible.
     * This method is called if more space is needed.
     *
     * @throws SQLException if no more space could be freed
     */
    void freeUpDiskSpace() throws SQLException;

    /**
     * Called when the checksum was invalid.
     *
     * @throws SQLException if this should not be ignored
     */
    void handleInvalidChecksum() throws SQLException;

    /**
     * Compare two values.
     *
     * @param a the first value
     * @param b the second value
     * @return 0 for equal, 1 if a is larger than b, and -1 otherwise
     */
    int compareTypeSave(Value a, Value b) throws SQLException;

    /**
     * Get the maximum length of a in-place large object
     *
     * @return the maximum size
     */
    int getMaxLengthInplaceLob();

    /**
     * Get the compression algorithm used for large objects.
     *
     * @param type the data type (CLOB or BLOB)
     * @return the compression algorithm, or null
     */
    String getLobCompressionAlgorithm(int type);

    /**
     * Get the next object id.
     *  This method is not required if LOB_FILES_IN_DIRECTORIES is enabled.
     *
     * @param needFresh if a fresh id is required
     * @param dataFile true if the id is for the data file
     * @return the new id
     */
    int allocateObjectId(boolean needFresh, boolean dataFile);

    /**
     * Create a temporary file and return the file name.
     *
     * @return the file name
     */
    String createTempFile() throws SQLException;

    /**
     * Get the synchronization object for lob operations.
     *
     * @return the synchronization object
     */
    Object getLobSyncObject();

}