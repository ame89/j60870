/*
 * Copyright 2014 Fraunhofer ISE
 *
 * This file is part of j60870.
 * For more information visit http://www.openmuc.org
 *
 * j60870 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * j60870 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with j60870.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.openmuc.j60870;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * The application service data unit (ASDU). The ASDU is the payload of the application protocol data unit (APDU). Its
 * structure is defined in IEC 60870-5-101. The ASDU consists of the Data Unit Identifier and a number of Information
 * Objects. The Data Unit Identifier contains:
 * <p/>
 * <ul>
 * <li>{@link org.openmuc.j60870.TypeId} (1 byte)</li>
 * <li>Variable Structure Qualifier (1 byte) - specifies how many Information Objects and Information Element sets are
 * part of the ASDU.</li>
 * <li>Cause of Transmission (COT, 1 or 2 bytes) - The first byte codes the actual
 * {@link org.openmuc.j60870.CauseOfTransmission}, a bit indicating whether the message was sent for test purposes only
 * and a bit indicating whether a confirmation message is positive or negative. The optional second byte of the Cause of
 * Transmission field is the Originator Address. It is the address of the originating controlling station so that
 * responses can be routed back to it.</li>
 * <li>Common Address of ASDU (1 or 2 bytes) - the address of the target station or the broadcast address. If the field
 * length of the common address is 1 byte then the addresses 1 to 254 are used to address a particular station (station
 * address) and 255 is used for broadcast addressing. If the field length of the common address is 2 bytes then the
 * addresses 1 to 65534 are used to address a particular station and 65535 is used for broadcast addressing. Broadcast
 * addressing is only allowed for certain TypeIDs.</li>
 * <li>A list of Information Objects containing the actual actual data in the form of Information Elements.</li>
 * </ul>
 *
 * @author Stefan Feuerhahn
 */
public class ASdu {

    private final TypeId typeId;
    private final boolean isSequenceOfElements;
    private final CauseOfTransmission causeOfTransmission;
    private final boolean test;
    private final boolean negativeConfirm;
    private final int originatorAddress;
    private final int commonAddress;
    private final InformationObject[] informationObjects;
    private final byte[] privateInformation;
    private final int sequenceLength;

    /**
     * Use this constructor to create standardized ASDUs.
     *
     * @param typeId               type identification field that defines the purpose and contents of the ASDU
     * @param isSequenceOfElements if false then the ASDU contains a sequence of information objects consisting of a fixed number of
     *                             information elements. If true the ASDU contains a single information object with a sequence of
     *                             elements.
     * @param causeOfTransmission  the cause of transmission
     * @param test                 true if the ASDU is sent for test purposes
     * @param negativeConfirm      true if the ASDU is a negative confirmation
     * @param originatorAddress    the address of the originating controlling station so that responses can be routed back to it
     * @param commonAddress        the address of the target station or the broadcast address.
     * @param informationObjects   the information objects containing the actual actual data
     */
    public ASdu(TypeId typeId,
                boolean isSequenceOfElements,
                CauseOfTransmission causeOfTransmission,
                boolean test,
                boolean negativeConfirm,
                int originatorAddress,
                int commonAddress,
                InformationObject[] informationObjects) {

        this.typeId = typeId;
        this.isSequenceOfElements = isSequenceOfElements;
        this.causeOfTransmission = causeOfTransmission;
        this.test = test;
        this.negativeConfirm = negativeConfirm;
        this.originatorAddress = originatorAddress;
        this.commonAddress = commonAddress;
        this.informationObjects = informationObjects;
        privateInformation = null;
        if (isSequenceOfElements) {
            sequenceLength = informationObjects[0].getInformationElements().length;
        } else {
            sequenceLength = informationObjects.length;
        }
    }

    /**
     * Use this constructor to create private ASDU with TypeIDs in the range 128-255.
     *
     * @param typeId               type identification field that defines the purpose and contents of the ASDU
     * @param isSequenceOfElements if false then the ASDU contains a sequence of information objects consisting of a fixed number of
     *                             information elements. If true the ASDU contains a single information object with a sequence of
     *                             elements.
     * @param sequenceLength       the number of information objects or the number elements depending depending on which is transmitted
     *                             as a sequence
     * @param causeOfTransmission  the cause of transmission
     * @param test                 true if the ASDU is sent for test purposes
     * @param negativeConfirm      true if the ASDU is a negative confirmation
     * @param originatorAddress    the address of the originating controlling station so that responses can be routed back to it
     * @param commonAddress        the address of the target station or the broadcast address.
     * @param privateInformation   the bytes to be transmitted as payload
     */
    public ASdu(TypeId typeId,
                boolean isSequenceOfElements,
                int sequenceLength,
                CauseOfTransmission causeOfTransmission,
                boolean test,
                boolean negativeConfirm,
                int originatorAddress,
                int commonAddress,
                byte[] privateInformation) {

        this.typeId = typeId;
        this.isSequenceOfElements = isSequenceOfElements;
        this.causeOfTransmission = causeOfTransmission;
        this.test = test;
        this.negativeConfirm = negativeConfirm;
        this.originatorAddress = originatorAddress;
        this.commonAddress = commonAddress;
        informationObjects = null;
        this.privateInformation = privateInformation;
        this.sequenceLength = sequenceLength;
    }

    ASdu(DataInputStream is, ConnectionSettings settings, int aSduLength) throws IOException {

        int typeIdCode = is.read();

        typeId = TypeId.createTypeId(typeIdCode);

        if (typeId == null) {
            throw new IOException("Unknown Type Identification: " + typeIdCode);
        }

        int tempbyte = is.read();

        isSequenceOfElements = (tempbyte & 0x80) == 0x80;

        int numberOfSequenceElements;
        int numberOfInformationObjects;

        sequenceLength = tempbyte & 0x7f;
        if (isSequenceOfElements) {
            numberOfSequenceElements = sequenceLength;
            numberOfInformationObjects = 1;
        } else {
            numberOfInformationObjects = sequenceLength;
            numberOfSequenceElements = 1;
        }

        tempbyte = is.read();
        causeOfTransmission = CauseOfTransmission.createCauseOfTransmission(tempbyte & 0x3f);
        test = (tempbyte & 0x80) == 0x80;
        negativeConfirm = (tempbyte & 0x40) == 0x40;

        if (settings.cotFieldLength == 2) {
            originatorAddress = is.read();
            aSduLength--;
        } else {
            originatorAddress = -1;
        }

        if (settings.commonAddressFieldLength == 1) {
            commonAddress = is.read();
        } else {
            commonAddress = is.read() + ((is.read() << 8));
            aSduLength--;
        }

        if (typeIdCode < 128) {

            informationObjects = new InformationObject[numberOfInformationObjects];

            for (int i = 0; i < numberOfInformationObjects; i++) {
                informationObjects[i] = new InformationObject(is,
                                                              typeId,
                                                              numberOfSequenceElements,
                                                              settings);
            }

            privateInformation = null;

        } else {
            informationObjects = null;
            privateInformation = new byte[aSduLength - 4];
            is.readFully(privateInformation);
        }

    }

    public TypeId getTypeIdentification() {
        return typeId;
    }

    public boolean isSequenceOfElements() {
        return isSequenceOfElements;
    }

    public int getSequenceLength() {
        return sequenceLength;
    }

    public CauseOfTransmission getCauseOfTransmission() {
        return causeOfTransmission;
    }

    public boolean isTestFrame() {
        return test;
    }

    public boolean isNegativeConfirm() {
        return negativeConfirm;
    }

    public Integer getOriginatorAddress() {
        return originatorAddress;
    }

    public int getCommonAddress() {
        return commonAddress;
    }

    public InformationObject[] getInformationObjects() {
        return informationObjects;
    }

    public byte[] getPrivateInformation() {
        return privateInformation;
    }

    int encode(byte[] buffer, int i, ConnectionSettings settings) {

        int origi = i;

        buffer[i++] = (byte) typeId.getCode();
        if (isSequenceOfElements) {
            buffer[i++] = (byte) (sequenceLength | 0x80);
        } else {
            buffer[i++] = (byte) sequenceLength;
        }

        if (test) {
            if (negativeConfirm) {
                buffer[i++] = (byte) (causeOfTransmission.getCode() | 0xC0);
            } else {
                buffer[i++] = (byte) (causeOfTransmission.getCode() | 0x80);
            }
        } else {
            if (negativeConfirm) {
                buffer[i++] = (byte) (causeOfTransmission.getCode() | 0x40);
            } else {
                buffer[i++] = (byte) causeOfTransmission.getCode();
            }
        }

        if (settings.cotFieldLength == 2) {
            buffer[i++] = (byte) originatorAddress;
        }

        buffer[i++] = (byte) commonAddress;

        if (settings.commonAddressFieldLength == 2) {
            buffer[i++] = (byte) (commonAddress >> 8);
        }

        if (informationObjects != null) {
            for (InformationObject informationObject : informationObjects) {
                i += informationObject.encode(buffer, i, settings);
            }
        } else {
            System.arraycopy(privateInformation, 0, buffer, i, privateInformation.length);
            i += privateInformation.length;
        }
        return i - origi;
    }

    @Override
    public String toString() {

        StringBuilder builder = new StringBuilder("Type ID: "
                                                  + typeId.getCode()
                                                  + ", "
                                                  + typeId
                                                  + ", "
                                                  + typeId.getDescription()
                                                  + "\nCause of transmission: "
                                                  + causeOfTransmission
                                                  + ", test: "
                                                  + isTestFrame()
                                                  + ", negative con: "
                                                  + isNegativeConfirm()
                                                  + "\nOriginator address: "
                                                  + originatorAddress
                                                  + ", Common address: "
                                                  + commonAddress);

        if (informationObjects != null) {
            for (InformationObject informationObject : informationObjects) {
                builder.append("\n");
                builder.append(informationObject.toString());
            }
        } else {
            builder.append("\nPrivate Information:\n");
            int l = 1;
            for (byte b : privateInformation) {
                if ((l != 1) && ((l - 1) % 8 == 0)) {
                    builder.append(' ');
                }
                if ((l != 1) && ((l - 1) % 16 == 0)) {
                    builder.append('\n');
                }
                l++;
                builder.append("0x");
                String hexString = Integer.toHexString(b & 0xff);
                if (hexString.length() == 1) {
                    builder.append(0);
                }
                builder.append(hexString + " ");
            }
        }

        return builder.toString();

    }

}
