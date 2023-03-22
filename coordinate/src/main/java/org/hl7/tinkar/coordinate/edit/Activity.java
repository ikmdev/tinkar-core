package org.hl7.tinkar.coordinate.edit;

import org.hl7.tinkar.common.binary.*;

public enum Activity implements Encodable {

    // @TODO probably should have concepts with descriptions rather than depending on the user strings here...
    VIEWING("Viewing"),
    DEVELOPING("Developing"),
    PROMOTING("Promoting"),
    MODULARIZING("Modularizing");

    private String userString;

    Activity(String userString) {
        this.userString = userString;
    }

    @Decoder
    public static Activity decode(DecoderInput in) {
        switch (Encodable.checkVersion(in)) {
            default:
                return Activity.valueOf(in.readString());
        }
    }


    // Using a static method rather than a constructor eliminates the need for
    // a readResolve method, but allows the implementation to decide how
    // to handle special cases.

    public String toUserString() {
        return this.userString;
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        out.writeString(name());
    }
}
