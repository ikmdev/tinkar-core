package org.hl7.tinkar.coordinate.edit;

import org.hl7.tinkar.common.binary.*;

public enum Activity implements Encodable {

    // @TODO probably should have concepts with descriptions rather than depending on the user strings here...
    VIEWING("Viewing"),
    DEVELOPING("Developing"),
    PROMOTING("Promoting"),
    MODULARIZING("Modularizing");

    private static final int marshalVersion = 1;

    private String userString;

    Activity(String userString) {
        this.userString = userString;
    }

    public String toUserString() {
        return this.userString;
    }


    // Using a static method rather than a constructor eliminates the need for
    // a readResolve method, but allows the implementation to decide how
    // to handle special cases.

    @Decoder
    public static Activity decode(DecoderInput in) {
        int objectMarshalVersion = in.readInt();
        switch (objectMarshalVersion) {
            case marshalVersion:
                return Activity.valueOf(in.readString());
            default:
                throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
        }
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        out.writeInt(marshalVersion);
        out.writeString(name());
    }

}
