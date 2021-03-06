/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package I2CCommunication;

import Commands.Acceleration;
import Commands.CalibParam;
import Commands.Calibrate;
import Commands.CloseTray;
import Commands.Commando;
import Commands.Light;
import Commands.Move;
import Commands.OpenTray;
import Commands.StateRequest;
import Commands.Suction;
import Commands.Velocity;
import Status.Busy;
import Status.EMC;
import Status.ElevatorLimitTrigg;
import Status.EncoderOutOfRange;
import Status.EncoderOutOfSync;
import Status.FlagPos;
import Status.LinearBotLimitTrigged;
import Status.Parameters;
import Status.ReadyToRecieve;
import Status.SafetySwitchLower;
import Status.SafetySwitchUpper;
import Status.Status;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.platform.Platform;
import com.pi4j.platform.PlatformAlreadyAssignedException;
import com.pi4j.platform.PlatformManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.Map;
import java.util.LinkedList;

/**
 * This communication class holds the respectively i2c devices used for the i2c
 * communication. Sending and recieving via i2c should be done via this class.
 * It works as an information relay. Recieve commando and send the appropriate
 * data to the respective controllers.
 *
 *
 * @author PerEspen
 */
public class I2CCommunication extends Thread
{

    /*FROM THE ARDUINO/Communication TO THE JAVA PROGRAM*/
 /*private static final byte BUSY = 0x50;
    private static final byte READY_TO_RECIEVE = 0x51;
    private static final byte EMC = 0x60;
    private static final byte SAFETY_SWITCH_UPPER = 0x61;
    private static final byte SAFETY_SWITCH_LOWER = 0x62;
    private static final byte ELEV_LIMIT_TRIGG = 0x63;
    private static final byte LINEARBOT_LMIT_TRIGG = 0x64;
    private static final byte ENCODER_OUT_OF_SYNC = 0x65;
    private static final byte ENCODER_OUT_OF_RANGE = 0x66;
    private static final byte CALIB_PARAM = 0x70;
    private static final byte FLAG_POS = 0x71;
     */
    //i2c-dev bus used
    private static final int I2CbusNr = 4;
    private static final byte CONTROLLER_ADDR_ELEVATOR = 0x05;
    private static final byte CONTROLLER_ADDR_LINEARBOT = 0x03;

    //I2C Bus
    I2CBus i2cbus;
    //Controllers
    I2CDevice linearRobot;
    I2CDevice elevatorRobot;

    // boolean waitingLinearState;
    // boolean waitingElevatorState;
    Status elevatorState;
    Status linearBotState;

    /**
     * ENUM to hold all the addresses connected to the incomming states of the
     * arduinos
     */
    private enum State
    {
        Busy((byte) 0x50),
        ReadyToRecieve((byte) 0x51),
        EMC((byte) 0x60),
        SAFETY_SWITCH_UPPER((byte) 0x61),
        SAFETY_SWITCH_LOWER((byte) 0x62),
        ELEV_LIMIT_TRIGG((byte) 0x63),
        LINEARBOT_LMIT_TRIGG((byte) 0x64),
        ENCODER_OUT_OF_SYNC((byte) 0x65),
        ENCODER_OUT_OF_RANGE((byte) 0x66),
        PARAMETER((byte) 0x70),
        FLAG_POS((byte) 0x71);

        //Hashmap for lookup
        private static final HashMap<Byte, State> lookup = new HashMap<Byte, State>();

        //Put the states with the accompanied value in the hashmap
        static
        {
            //Create reverse lookup hash map 
            for (State s : State.values())
            {
                lookup.put(s.getStateValue(), s);
            }
        }
        //Satus address
        private byte stateAddr;

        private State(byte stateAddress)
        {
            this.stateAddr = stateAddress;
        }

        public byte getStateValue()
        {
            return stateAddr;
        }

        public static State get(byte address)
        {
            //the reverse lookup by simply getting 
            //the value from the lookup HsahMap. 
            return lookup.get(address);
        }
    }

    //Lists to keep incomming demands in queue
    LinkedList<Commando> sendQeue;
    LinkedList<Commando> recieveQeue;

    HashMap<Byte, Status> statusMap;
    ArrayList<Byte> statusList;

    public I2CCommunication()
    {
        //Create the recieve send lists
        recieveQeue = new LinkedList<Commando>();
        sendQeue = new LinkedList<Commando>();

        //hashmap for status vs byte value
        // fillStatusMap(statusMap);
        //fillStatusList(statusList);
        elevatorState = null;
        linearBotState = null;
        initiate();
    }

    @Override
    public void run()
    {
        while (true)
        {
            if (!sendQeue.isEmpty())
            {    //Send the commands in the qeue
                sendCommand(sendQeue.pop());
                // Only recieve if something is sent
                //TODO: Check this, currently the incomming recieving qeue only can recieve stateRequest, maybe staterequest should be Status 
                //and thereof the incomming demand can handle all kind of "requests" for different states

            }
            if (!recieveQeue.isEmpty())
            {//read the expecting incomming bytes
                //Find the status to create and put all the values in the status
                //Trigger the status listener
                requestStatus(recieveQeue.pop());

                if (elevatorState != null || linearBotState != null)
                {
                    checkStatesAndTrigger(elevatorState, linearBotState);
                } else
                {
                    System.out.println("ONE OF THE STATES WERE NULL");
                }
            }
        }

    }

    /*
    private void fillStatusMap(HashMap statusMap)
    {
        statusMap.put(, new Busy(1));
        statusMap.put(EMC, new EMC(1));
        statusMap.put(ELEV_LIMIT_TRIGG, new ElevatorLimitTrigg(1));
        statusMap.put(ENCODER_OUT_OF_RANGE, new EncoderOutOfRange(1));
        statusMap.put(ENCODER_OUT_OF_SYNC, new EncoderOutOfSync(1));
        statusMap.put(FLAG_POS, new FlagPos(1));
        statusMap.put(LINEARBOT_LMIT_TRIGG, new LinearBotLimitTrigged(1));
        statusMap.put(READY_TO_RECIEVE, new ReadyToRecieve(1));
        statusMap.put(SAFETY_SWITCH_LOWER, new SafetySwitchLower(1));
        statusMap.put(SAFETY_SWITCH_UPPER, new SafetySwitchUpper(1));

    }
     */
 /*
    public void testState()
    {
           State state = null;
        state.get(request.getCmdAddr());
        
        state.getStateValue();
    }
    
     */
    /**
     * Handle the task of sending StateRequest to the defined controllers Reads
     * the return bytes and makes a state of them Updates the global state for
     * each respective controller
     *
     * @param request The given StateRequest
     */
    private void requestStatus(Commando request)
    {

        //TODO: Dont think casting is needed here, as makeState, checks what state the incomming message is.
        System.out.print("Request addr:");
        System.out.println(request.getCmdAddr());

        //Storing of bytes
        byte[] returnByteLinearBot = null;
        byte[] returnByteElevator = null;
        //TODO: Fix this staterequest, should maybe be commando. MAYBE REMOVE THE CHECKING
        //When request commando is staterequest both arduinos should be addressed
            /*
        if (request instanceof StateRequest)
        {
            StateRequest cmdStqry = (StateRequest) request;
            System.out.print("Sending request and waiting for query: ");
            returnByteLinearBot = readByteFromAddr(linearRobot, cmdStqry.getCmdAddr(), cmdStqry.getNrOfBytes());
            returnByteElevator = readByteFromAddr(elevatorRobot, cmdStqry.getCmdAddr(), cmdStqry.getNrOfBytes());
                 

        } else if (request instanceof CalibParam)
        {
            //Cast to get the correct command address
            CalibParam cmdCalibPar = (CalibParam) request;
            System.out.print("Command CalibParam");

            //TODO: Commented out returnbyte elevator;
            returnByteLinearBot = readByteFromAddr(linearRobot, cmdCalibPar.getCmdAddr(), cmdCalibPar.getNrOfBytes());
            returnByteElevator = readByteFromAddr(elevatorRobot,cmdCalibPar.getCmdAddr() , cmdCalibPar.getNrOfBytes());

        } //If nothing of the Request commands were recognised, just send a general request
        */
        if(request != null)
        {
            returnByteLinearBot = readByteFromAddr(linearRobot, request.getCmdAddr(), request.getNrOfBytes());
            returnByteElevator = readByteFromAddr(elevatorRobot, request.getCmdAddr() , request.getNrOfBytes());
        }

        /***Making the states and putting the payload inside the status message***/
        if (returnByteLinearBot != null)
        {
            System.out.print("1st byte from returnByteLinearBot:");
            System.out.println(returnByteLinearBot[0]);
            System.out.print("Making linear bot state");
            linearBotState = makeState(returnByteLinearBot);
            System.out.print("Made state: ");
 //           System.out.print(linearBotState.getString());
            //Put the value read from the i2c comm to the desired state
            //Removes first byte because that is address ant not value
            byte[] valueArr = Arrays.copyOfRange(returnByteLinearBot, 1, returnByteLinearBot.length);
            //Checking if there is any values to put in the status
            if(valueArr.length != 0 && linearBotState != null)
            linearBotState.putValue(valueArr);
        }
        
        if (returnByteElevator != null)
        {
             System.out.print("1st byte from returnByteElevatorBot:");
            System.out.println(returnByteElevator[0]);
            System.out.print("Making elevator bot state");
            elevatorState = makeState(returnByteElevator);
            System.out.print("Made state: ");
          //  System.out.print(elevatorState.getString());
         
            
             byte[] valueArr = Arrays.copyOfRange(returnByteElevator, 1, returnByteElevator.length);
            //Checking if there is any values to put in the status
            if(valueArr.length != 0 && elevatorState != null)
            elevatorState.putValue(valueArr);
        }

        //Find the retrievend command and preform the State Update
        //updateState(cmdReg.findCommand(returnByteElevator[0]));
        // updateState(cmdReg.findCommand(returnByteLinear[0]));
        // checkState(cmdReg.findCommand(returnByteElevator[0]), cmdReg.findCommand(returnByteLinear[0]));
        //Reset the state request
        //((StateRequest) cmd).reset();
    }

    /**
     * Check for the readytorecieve state and trigger if both are ready Else
     * trigger the states which are not ready to recieve
     *
     * @param elevatorState The elevator State
     * @param linearBotState The linearbot state
     */
    private void checkStatesAndTrigger(Status elevatorState, Status linearBotState)
    {
        //Safeguarding against null-pointer
        if (elevatorState != null && linearBotState != null)
        {
            System.out.println(elevatorState.equals(State.ReadyToRecieve));
            if((Byte.compare(elevatorState.getStatusAddress(), State.ReadyToRecieve.getStateValue()) == 0) && (Byte.compare(linearBotState.getStatusAddress(), State.ReadyToRecieve.getStateValue()) == 0))
            {
                
                //TODO: Trigger ready to recieve
                System.out.println("ReadyToRecieve triggered");
            } 
            if (Byte.compare(elevatorState.getStatusAddress(), State.ReadyToRecieve.getStateValue()) != 0)
            {
                //TODO: Trigger this state / send notify
                System.out.println("Elevator state triggered");
            } 
            if (Byte.compare(linearBotState.getStatusAddress(), State.ReadyToRecieve.getStateValue()) != 0)
            {
                //TODO: Trigger this state
                System.out.println("LinearBotState triggered");
            }
        }
        System.out.println("Checking done");
    }

    /**
     * Make a state from the given statebyte[]
     *
     * @param stateByte Statebyte to create state from
     * @return Returns the created state, else null!
     */
    //TODO: MAKE ENUM TO DECIDE WHICH STATUS HAS BEEN SENT FROM ARDUINO
    public Status makeState(byte[] stateByte)
    {
        byte cmdAddr = stateByte[0];
        Status returnState = null;
        System.out.println("State addr: ");
        System.out.print(cmdAddr);

        State state = State.get(cmdAddr);

        System.out.println("Value:");

        //Nullpointer check
        if (state != null)
        {
            System.out.println(state.getStateValue());
        

            if (state.equals(state.Busy))
            {
                System.out.println("State equals busy");
                returnState = new Busy();
            } else if (state.equals(state.EMC))
            {
                System.out.println("State equals EMC");
                returnState = new EMC();
            } else if (state.equals(state.ReadyToRecieve))
            {
                System.out.println("State equals Ready");
                returnState = new ReadyToRecieve();
            } else if (state.equals(state.ENCODER_OUT_OF_SYNC))
            {
                System.out.println("State equals encoder");
                returnState = new EncoderOutOfSync();
            } else if (state.equals(state.ENCODER_OUT_OF_RANGE))
            {
                System.out.println("State equals encoder our of range");
                returnState = new EncoderOutOfRange();
            } else if (state.equals(state.PARAMETER))
            {
                returnState = new Parameters();
            }

            System.out.print("Return state: ");
            System.out.print(returnState.getStatusAddress());
            System.out.print(", " + returnState.getString());
        }
        else
            System.out.println("State not recognised!!!");

        return returnState;
    }

    /**
     * Add to the sendqueue, only commands
     *
     * @param cmd Commando to be performed
     */
    public void addSendQ(Commando cmd)
    {
        sendQeue.add(cmd);
    }

    /**
     * Added to the recieving queue
     *
     * @param stat
     */
    //TODO: Make changes to this recieving thing
    public void addRecieveQ(Commando stat)
    {
        recieveQeue.add(stat);
    }

    /**
     * Sets up the I2C bus with platform and initiates the connection
     */
    private void initiate()
    {
        try
        {
            try
            {
                PlatformManager.setPlatform(Platform.ODROID);

            } catch (PlatformAlreadyAssignedException ex)
            {
                Logger.getLogger(I2CCommunication.class.getName()).log(Level.SEVERE, null, ex);
            }
            // get the I2C bus to communicate on

            i2cbus = I2CFactory.getInstance(I2CbusNr);
            elevatorRobot = i2cbus.getDevice(CONTROLLER_ADDR_ELEVATOR);
            linearRobot = i2cbus.getDevice(CONTROLLER_ADDR_LINEARBOT);

        } catch (I2CFactory.UnsupportedBusNumberException ex)
        {
            Logger.getLogger(I2CCommunication.class.getName()).log(Level.SEVERE, null, ex);

        } catch (IOException ex)
        {
            Logger.getLogger(I2CCommunication.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Read the incomming message from the device
     *
     * @param device The device to read from
     * @return Return the incomming byte from the
     */
    private byte readByte(I2CDevice device)
    {
        return readByte(device);
    }

    /**
     * Read from the given register address and buffer the answer in the byte[]
     *
     * @param device Device to read from
     * @param address The register address specified
     * @param byteSize Size of the return buffer byte
     * @return Returns a read buffer from the given i2cdevice with given
     * bytesize
     */
    private byte[] readByteFromAddr(I2CDevice device, byte address, int byteSize)
    {
        //Fields
        byte[] returnByte = new byte[byteSize];
        int offset = 0;
        //Store number of bytes actually read
        int bytesRead = 0;
        try
        {
            bytesRead = device.read(address, returnByte, offset, byteSize);

        } catch (IOException ex)
        {
            Logger.getLogger(I2CCommunication.class
                    .getName()).log(Level.SEVERE, null, ex);
        }

        //create array with exact amount of bytes
        byte[] modifiedReturnByte = resizeArray(returnByte, (byte) -1);

        return modifiedReturnByte;
    }

    /**
     * Resize an array with only carrying information, -1 is considered as not
     * valuable information.
     *
     * @param inputArr
     * @return Return an resized array
     */
    private byte[] resizeArray(byte[] inputArr, byte resizeOption)
    {
        int length = inputArr.length;
        int cnt = 0;
        //Find the actual length of the array

        for (int i = 0; i < length; ++i)
        {
            if (Byte.compare(inputArr[i], resizeOption) != 0)
            {
                ++cnt;
            }

        }
        //Create the new byte[]
        byte[] returnByte = new byte[cnt];
        //Copy the wanted values
        System.arraycopy(inputArr, 0, returnByte, 0, cnt);
        //Return the resized byte[]
        return returnByte;
    }

    /**
     * Read from the given register address and buffer the answer in the byte[]
     *
     * @param device Device to read from
     * @param address The register address specified
     * @param byteSize Size of the buffer byte
     * @return Returns a read buffer from the given i2cdevice with given
     * bytesize
     */
    private int readBytes(I2CDevice device, byte[] buffer, int byteSize)
    {
        byte[] returnByte = new byte[byteSize];
        int offset = 0;
        int bytesRead = 0;

        try
        {
            bytesRead = device.read(buffer, offset, byteSize);
        } catch (IOException ex)
        {
            Logger.getLogger(I2CCommunication.class.getName()).log(Level.SEVERE, null, ex);
        }

        return bytesRead;
    }

    /**
     * Write a byte to the given i2c device in the param, does not carry a
     * register address to be read first
     *
     * @param device The device to wrtie byte to
     * @param sendByte The byte to be sent
     */
    private void writeByte(I2CDevice device, byte sendByte)
    {
        try
        {
            device.write(sendByte);

        } catch (IOException ex)
        {
            Logger.getLogger(I2CCommunication.class
                    .getName()).log(Level.SEVERE, null, ex);
            System.out.println("Communication.Communication.writeByte(): WRITE GAVE IO-EXCEPTION");
        }
    }

    /**
     * Write byte[] to the specified device with the specified cmd.
     *
     * @param device The I2CDevice to write to
     * @param sendByte The byte[] to send to respective i2c device
     * @param sendAddress The register address for the sent byte[]
     */
    private void writeByteToAddr(I2CDevice device, byte sendByte, byte sendAddress)
    {
        try
        {
            device.write(sendAddress, sendByte);

        } catch (IOException ex)
        {
            Logger.getLogger(I2CCommunication.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("Communication.Communication.writeByteToAddr(): WRITE GAVE IO-EXCEPTION");
        }
    }

    /**
     * Write a byte[] to the given i2c device in the param, does not carry a
     * register address to be read first
     *
     * @param device The device to wrtie byte to
     * @param sendByte The byte[] to be sent
     */
    private void writeBytes(I2CDevice device, byte[] sendByte)
    {
        try
        {
            device.write(sendByte);

        } catch (IOException ex)
        {
            Logger.getLogger(I2CCommunication.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("Communication.Communication.writeByte(): WRITE GAVE IO-EXCEPTION");
        }
    }

    /**
     * Write a byte[] to the given i2c device in the param, does not carry a
     * register address to be read first
     *
     * @param device The device to wrtie byte to
     * @param sendByte The byte[] to be sent
     */
    private void writeBytesToAddr(I2CDevice device, byte cmdAddr, byte[] sendByte)
    {
        try
        {
            device.write(cmdAddr, sendByte);

        } catch (IOException ex)
        {
            Logger.getLogger(I2CCommunication.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("Communication.Communication.writeByte(): WRITE GAVE IO-EXCEPTION");
        }
    }

    private void testCommando()
    {
        byte b = 0b00000001;
        byte b2 = 100;
        Commando comm = new Commando(b);
        int i = 15;
        System.out.println("Setting int value");
        comm.setIntValue(i);
        System.out.println(comm.getIntValue());

    }

    /**
     * Handles the commandos given in parameter. Tasks handled based on Commando
     * subclass.
     *
     * @param cmd The commando to perform
     */
    public void sendCommand(Commando cmd)
    {
        System.out.print("Commando address: ");
        System.out.println(cmd.getCmdAddr());

        /**
         * COMMANDS TO ARDUINO*
         */
        /**
         * *Checking all the possible commands**
         */
        //Check for move command
        if (cmd instanceof Move)
        {
            doMove(cmd);
        } //Check for acceleration command
        else if (cmd instanceof Acceleration)
        {
            //Cast and send the Acceleration parameters
            Acceleration cmdAccl = (Acceleration) cmd;
            if (!(cmdAccl.getElevatorAcclParam().equals(null)))
            {
                this.writeBytesToAddr(elevatorRobot, cmd.getCmdAddr(), cmdAccl.getElevatorAcclParam());
            }
            if (!(cmdAccl.getLinearRobotAcclParam().equals(null)))
            {
                this.writeBytesToAddr(linearRobot, cmd.getCmdAddr(), cmdAccl.getLinearRobotAcclParam());
            }

        } //Check for calibrate command and do the tasks   
        //Send the calibrate command
        else if (cmd instanceof Calibrate)
        {
            writeByte(linearRobot, cmd.getCmdAddr());
            writeByte(elevatorRobot, cmd.getCmdAddr());
        } //Check for suction command
        else if (cmd instanceof Suction)
        {
            //Do the suction
            Suction cmdSuction = (Suction) cmd;
            this.writeBytesToAddr(linearRobot, cmd.getCmdAddr(), cmd.getValue());
            this.writeBytesToAddr(elevatorRobot, cmd.getCmdAddr(), cmd.getValue());
        } //Check for velocity command
        else if (cmd instanceof Velocity)
        {
            //Send the new velocity params
            Suction cmdSuction = (Suction) cmd;
            this.writeBytesToAddr(linearRobot, cmd.getCmdAddr(), cmd.getValue());
            this.writeBytesToAddr(elevatorRobot, cmd.getCmdAddr(), cmd.getValue());
        } else if (cmd instanceof OpenTray)
        {
            //Control the gripper
            OpenTray cmdOpenTray = (OpenTray) cmd;
            this.writeBytesToAddr(linearRobot, cmdOpenTray.getCmdAddr(), cmdOpenTray.getValue());
            this.writeBytesToAddr(elevatorRobot, cmdOpenTray.getCmdAddr(), cmdOpenTray.getValue());
        }
         else if (cmd instanceof CloseTray)
        {
            //Control the gripper
            CloseTray cmdCloseTray = (CloseTray) cmd;
            this.writeBytesToAddr(linearRobot, cmdCloseTray.getCmdAddr(), cmdCloseTray.getValue());
            this.writeBytesToAddr(elevatorRobot, cmdCloseTray.getCmdAddr(), cmdCloseTray.getValue());
        }
        else if (cmd instanceof Light)
        {
            //Turn light on/off
            Light cmdLight = (Light) cmd;
            this.writeBytesToAddr(linearRobot, cmdLight.getCmdAddr(), cmdLight.getValue());
            this.writeBytesToAddr(elevatorRobot, cmdLight.getCmdAddr(), cmdLight.getValue());
        } else if (cmd instanceof CalibParam)
        {
            CalibParam cmdCalPar = (CalibParam) cmd;
            this.writeBytesToAddr(linearRobot, cmdCalPar.getCmdAddr(), cmdCalPar.getValue());
            this.writeBytesToAddr(elevatorRobot, cmdCalPar.getCmdAddr(), cmdCalPar.getValue());
        } else
        {
            //TODO: Maybe throw exception?
            System.out.println("THE COMMAND WAS NOT RECOGNISED");
            
            this.writeBytesToAddr(linearRobot, cmd.getCmdAddr(), cmd.getValue());
            this.writeBytesToAddr(elevatorRobot, cmd.getCmdAddr(), cmd.getValue()); 
        }
        /**
         * COMMANDS "FROM" ARDUINO*
         */
        /*
        //Check for move command
        if (cmd instanceof StateRequest)
        {
            //Storing of bytes
            byte[] returnByteLinear = null;
            byte[] returnByteElevator = null;
            StateRequest cmdStqry = (StateRequest) cmd;
            //Check if StateRequest is for elevator robot
            if (cmdStqry.forElevatorRobot())
            {
                returnByteElevator = readByteFromAddr(elevatorRobot, cmd.getCmdAddr(), 1);
            }

            //Check if StateRequest is for linear robot
            if (cmdStqry.forLinearRobot())
            {
                returnByteLinear = readByteFromAddr(linearRobot, cmd.getCmdAddr(), 1);
            }
            //Find the retrievend command and preform the State Update
            //updateState(cmdReg.findCommand(returnByteElevator[0]));
            // updateState(cmdReg.findCommand(returnByteLinear[0]));
            //checkState(cmdReg.findCommand(returnByteElevator[0]), cmdReg.findCommand(returnByteLinear[0]));
            //Reset the state request
            //((StateRequest) cmd).reset();
        }
         */
    }

    /**
     * Do the move command as specified
     *
     * @param cmd The command with attached values
     */
    public void doMove(Commando cmd)
    {
        //Do the X-Y movement first and send to the controller
        Move cmdMove = (Move) cmd;
        byte[] xyByte = null;
        //Combine the xyByte from the cmd move
        if ((cmdMove.getxValue() != null) && (cmdMove.getyValue() != null))
        {
            xyByte = new byte[cmd.getNrOfBytes() + cmd.getNrOfBytes()];
            System.arraycopy(cmdMove.getxValue(), 0, xyByte, 0, cmdMove.getxValue().length);
            System.arraycopy(cmdMove.getyValue(), 0, xyByte, cmdMove.getxValue().length, cmdMove.getxValue().length);

            //Make new byte to send to store the byte[] length in the first byte
            byte[] sendByte = new byte[xyByte.length + 1];
            sendByte[0] = (byte) ((byte) cmdMove.getxValue().length + cmdMove.getyValue().length);
            System.arraycopy(xyByte, 0, sendByte, 1, sendByte[0]);

            System.out.println("Sending do move command to linearbot");
            //Write the bytes with the desired address
          writeBytesToAddr(linearRobot, cmdMove.getCmdAddr(), sendByte);
          
        }
        
        //Z value should be written to elevator robot
        
        if (cmdMove.getzValue() != null)
        {
            byte[] sendByte = new byte[xyByte.length + 1];
            sendByte[0] = (byte) cmdMove.getzValue().length;
            
            System.arraycopy(cmdMove.getzValue(), 0, sendByte, 1, sendByte[0]);
            
              System.out.println("Sending do move command to elevatorbot");
            writeBytesToAddr(elevatorRobot, cmdMove.getCmdAddr(), cmdMove.getzValue());
           
        }
        
        System.out.println("Sending done");
    }

    /**
     * Write specified bytes to the device
     *
     * @param device Device to send bytes to
     * @param sendBuff Byte[] to send
     * @param byteSize Number of bytes to send
     */
    private void writeBytesWithSize(I2CDevice device, byte[] sendBuff, int byteSize)
    {
        int offset = 0;
        try
        {
            device.write(sendBuff, offset, byteSize);
        } catch (IOException ex)
        {
            Logger.getLogger(I2CCommunication.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    /*
    private void fillStatusList(ArrayList<Byte> statusList)
    {
        statusList.add(BUSY);
        statusList.add(EMC);
        statusList.add(ELEV_LIMIT_TRIGG);
        statusList.add(ENCODER_OUT_OF_RANGE);
        statusList.add(ENCODER_OUT_OF_SYNC);
        statusList.add(FLAG_POS);
        statusList.add(READY_TO_RECIEVE);
        statusList.add(LINEARBOT_LMIT_TRIGG);
        statusList.add(SAFETY_SWITCH_LOWER);
        statusList.add(SAFETY_SWITCH_UPPER);
        statusList.add(READY_TO_RECIEVE);
    }
     */

}

/*
    /**
     * Set the State true for the respective device in param
     * @param device Device to set state for
     * @param cmd The state
 */
 /*
    private void updateState(I2CDevice device, Commando cmd)
    {
        if(device.equals(linearRobot))
            cmd.setLinearRobot(true);
        
        if(device.equals(elevatorRobot))
            cmd.setElevatorRobot(true);
    }
 */
 /*
    
    WAITING FOR READY TO RECIEVE
        //Keep sending coordinates until they give OK recieved message back
        while (!linearBotOk || !elevatorBotOk)
        {
            //Check the linear and elevator bot are ok
            if (!linearBotOk)
            {
                linearBotOk = readyState(linearRobot);
            }
            if (!elevatorBotOk)
            {
                elevatorBotOk = readyState(elevatorRobot);
            }
        }

        //Send the X-Y Movement
        if (linearBotOk)
        {
            writeByteToAddr(linearRobot, xyByte, cmd.getCmdAddr());
        }

        ///Send the Z movement
        if (elevatorBotOk)

        {
            writeByteToAddr(elevatorRobot, cmdMove.getzValue(), cmd.getCmdAddr());
        }

    
    
    
    
 */
