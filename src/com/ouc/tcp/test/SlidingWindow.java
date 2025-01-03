package com.ouc.tcp.test;

import com.ouc.tcp.client.Client;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.TCP_PACKET;

import java.util.*;

public class SlidingWindow {
    private Client client;
    public int cwnd = 1;
    private volatile int ssthresh = 16;
    private int count = 0;  
    private Hashtable<Integer, TCP_PACKET> packets = new Hashtable<>();
    private UDT_Timer timer;
    private int lastACKSequence = -1;
    private int lastACKSequenceCount = 0;

    public SlidingWindow(Client client) {
        this.client = client;
    }

    public boolean isFull() {
        return this.cwnd <= this.packets.size();
    }

    public void putPacket(TCP_PACKET packet) {
        int currentSequence = (packet.getTcpH().getTh_seq() - 1) / 100;
        this.packets.put(currentSequence, packet);

        if (this.timer == null) {
            this.timer = new UDT_Timer();
            this.timer.schedule(new RetransmitTask(this), 3000, 3000);
        }
    }

    public void receiveACK(int currentSequence) {
        if (currentSequence == this.lastACKSequence) {
            this.lastACKSequenceCount++;
            if (this.lastACKSequenceCount == 4) {
                TCP_PACKET packet = this.packets.get(currentSequence + 1);
                if (packet != null) {
                    this.client.send(packet);

                    if (this.timer != null) {
                        this.timer.cancel();
                    }
                    this.timer = new UDT_Timer();
                    this.timer.schedule(new RetransmitTask(this), 3000, 300);
                }

                fastRecovery();
            }
        } else {
            List sequenceList = new ArrayList(this.packets.keySet());
            Collections.sort(sequenceList);
            for (int i = 0; i < sequenceList.size() && (int) sequenceList.get(i) <= currentSequence; i++) {
                this.packets.remove(sequenceList.get(i));
            }

            if (this.timer != null) {
                this.timer.cancel();
            }

            if (this.packets.size() != 0) {
                this.timer = new UDT_Timer();
                this.timer.schedule(new RetransmitTask(this), 3000, 300);
            }

            this.lastACKSequence = currentSequence;
            this.lastACKSequenceCount = 1;

            if (this.cwnd < this.ssthresh) {
                this.cwnd++;
            } else {
                this.count++;
                if (this.count >= this.cwnd) {
                    this.count -= this.cwnd;
                    this.cwnd++;
                }
            }
        }
    }

    public void slowStart() {
        this.ssthresh = this.cwnd / 2;
        if (this.ssthresh < 2) {
            this.ssthresh = 2;
        }
        this.cwnd = 1;
    }

    public void fastRecovery() {
        this.ssthresh = this.cwnd / 2;
        if (this.ssthresh < 2) {
            this.ssthresh = 2;
        }
        this.cwnd = this.ssthresh;
    }

    public void retransmit() {
        this.timer.cancel();

        List sequenceList = new ArrayList(this.packets.keySet());
        Collections.sort(sequenceList);

            TCP_PACKET packet = this.packets.get(sequenceList.get(0));
            if (packet != null) {
                System.out.println("retransmit: " + (packet.getTcpH().getTh_seq() - 1) / 100);
                this.client.send(packet);
            }

        if (this.packets.size() != 0) {
            this.timer = new UDT_Timer();
            this.timer.schedule(new RetransmitTask(this), 3000, 3000);
        } else {
            System.out.println("no packet");
        }
    }
}

class RetransmitTask extends TimerTask {
    private SlidingWindow window;

    public RetransmitTask(SlidingWindow window) {
        this.window = window;
    }

    @Override
    public void run() {
        System.out.println("--- Time Out ---");
        this.window.slowStart();

        this.window.retransmit();
    }
}
