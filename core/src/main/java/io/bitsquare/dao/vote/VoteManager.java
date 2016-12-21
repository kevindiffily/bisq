/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.dao.vote;

import com.google.inject.Inject;
import io.bitsquare.btc.provider.fee.FeeService;
import io.bitsquare.btc.wallet.BtcWalletService;
import io.bitsquare.btc.wallet.SquWalletService;
import io.bitsquare.storage.Storage;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

public class VoteManager {
    private static final Logger log = LoggerFactory.getLogger(VoteManager.class);
    private final BtcWalletService btcWalletService;
    private final SquWalletService squWalletService;
    private FeeService feeService;
    private final Storage<ArrayList<VoteItemCollection>> voteItemCollectionsStorage;
    private ArrayList<VoteItemCollection> voteItemCollections = new ArrayList<>();
    private VoteItemCollection currentVoteItemCollection;

    @Inject
    public VoteManager(BtcWalletService btcWalletService, SquWalletService squWalletService, FeeService feeService, Storage<ArrayList<VoteItemCollection>> voteItemCollectionsStorage) {
        this.btcWalletService = btcWalletService;
        this.squWalletService = squWalletService;
        this.feeService = feeService;
        this.voteItemCollectionsStorage = voteItemCollectionsStorage;

        ArrayList<VoteItemCollection> persisted = voteItemCollectionsStorage.initAndGetPersistedWithFileName("VoteItemCollections");
        if (persisted != null)
            voteItemCollections.addAll(persisted);

        checkIfOpenForVoting();
    }

    private void checkIfOpenForVoting() {
        //TODO mock
        setCurrentVoteItemCollection(new VoteItemCollection());
    }

    public byte[] calculateHash(VoteItemCollection voteItemCollection) {
        List<Byte> bytesList = new ArrayList<>();
        // we add first the 16 bytes for compensationRequest votes
        voteItemCollection.stream().forEach(e -> {
            if (e instanceof CompensationRequestVoteItemCollection) {
                CompensationRequestVoteItemCollection collection = (CompensationRequestVoteItemCollection) e;
                bytesList.add(collection.code.code);
                int payloadSize = collection.code.payloadSize;
                if (payloadSize == 2) {
                    List<CompensationRequestVoteItem> items = collection.getCompensationRequestVoteItems();
                    int size = items.size();
                    BitSet bitSetVoted = new BitSet(size);
                    BitSet bitSetValue = new BitSet(size);
                    for (int i = 0; i < items.size(); i++) {
                        CompensationRequestVoteItem compensationRequestVoteItem = items.get(i);
                        log.error(compensationRequestVoteItem.toString());
                        boolean hasVoted = compensationRequestVoteItem.isHasVoted();
                        log.error("bitSetVoted " + hasVoted);
                        bitSetVoted.set(i, hasVoted);
                        if (hasVoted) {
                            boolean acceptedVote = compensationRequestVoteItem.isAcceptedVote();
                            checkArgument(acceptedVote == !compensationRequestVoteItem.isDeclineVote(), "Accepted must be opposite of declined value");
                            bitSetValue.set(i, acceptedVote);
                            log.error("bitSetValue " + acceptedVote);
                        } else {
                            bitSetValue.set(i, false);
                            log.error("bitSetValue  false");
                        }
                    }

                    byte[] bitSetVotedArray = bitSetVoted.toByteArray();
                    byte[] bitSetValueArray = bitSetValue.toByteArray();
                    for (int i = 0; i < bitSetVotedArray.length; i++) {
                        bytesList.add(bitSetVotedArray[i]);
                    }
                    for (int i = 0; i < bitSetValueArray.length; i++) {
                        bytesList.add(bitSetValueArray[i]);
                    }
                } else {
                    log.error("payloadSize is not as expected(1). payloadSize=" + payloadSize);
                }
            }
        });

        // After that we add the optional parameter votes
        voteItemCollection.stream().forEach(e -> {
            if (!(e instanceof CompensationRequestVoteItemCollection)) {
                if (e.hasVoted()) {
                    bytesList.add(e.code.code);
                    int payloadSize = e.code.payloadSize;
                    if (payloadSize == 1) {
                        byte value = e.getValue();
                        log.error("value : " + value);
                        log.error("value binary: " + Integer.toBinaryString(value));
                        bytesList.add(value);
                    } else {
                        log.error("payloadSize is not as expected(4). payloadSize=" + payloadSize);
                    }
                }
            }
        });

        byte[] bytes = new byte[bytesList.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = bytesList.get(i);
        }
        log.error("bytesList " + bytesList);
        log.error("bytes " + bytes);
        log.error("Hex " + Utils.HEX.encode(bytes));
        bytesList.forEach(e -> log.error("binary: " + Integer.toBinaryString(e)));


        return bytes;
    }

    byte[] toBytes(int i) {
        byte[] result = new byte[4];

        result[0] = (byte) (i >> 24);
        result[1] = (byte) (i >> 16);
        result[2] = (byte) (i >> 8);
        result[3] = (byte) (i /*>> 0*/);

        return result;
    }

    public void setVoteItemCollections(VoteItemCollection voteItemCollection) {
        //TODO check equals code
        if (!voteItemCollections.contains(voteItemCollection)) {
            voteItemCollections.add(voteItemCollection);
            voteItemCollectionsStorage.queueUpForSave(voteItemCollections, 500);
        }
    }

    public void setCurrentVoteItemCollection(VoteItemCollection currentVoteItemCollection) {
        this.currentVoteItemCollection = currentVoteItemCollection;
        setVoteItemCollections(currentVoteItemCollection);
    }

    public VoteItemCollection getCurrentVoteItemCollection() {
        return currentVoteItemCollection;
    }
}
