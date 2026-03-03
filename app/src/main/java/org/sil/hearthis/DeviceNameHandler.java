package org.sil.hearthis;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * Handler responds to HTTP request by returning a string, the name of this device.
 */
public class DeviceNameHandler {
    SyncService _parent;
    public DeviceNameHandler(SyncService parent) {
        _parent = parent;
    }

    public Response handle(NanoHTTPD.IHTTPSession session) {
        String resp = "John's Android";
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/plain", resp);
    }
}
