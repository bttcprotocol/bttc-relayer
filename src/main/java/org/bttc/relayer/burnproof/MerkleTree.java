package org.bttc.relayer.burnproof;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Hash;

@Component
@Slf4j
public class MerkleTree {

  private static final  String ZERO32 = "0000000000000000000000000000000000000000000000000000000000000000";
  private List<List<String>> layers = new ArrayList<>();
  private List<String> leaves = new ArrayList<>();

  /**
   * @description create tree
   * @param leavesIn  valid leaves
   * @return  root
   */
  public  MerkleTree(List<String> leavesIn) {
    if (CollectionUtils.isEmpty(leavesIn)) {
      return ;
    }
    int depth = (int)Math.ceil(Math.log(leavesIn.size())/Math.log(2));
    if (depth > 20) {
      log.error("Depth must be 20 or less");
      return ;
    }
    int leaveNumbers = (int)Math.pow(2, depth);
    List<String> layer1 = new ArrayList<>(leaveNumbers);
    List<String> layer1Zeors = new ArrayList<>(Collections.nCopies(leaveNumbers - leavesIn.size(), ZERO32));
    layer1.addAll(leavesIn);
    layer1.addAll(layer1Zeors);
    layers.add(layer1);
    leaves.addAll(leavesIn);
    createHashes(layer1);
  }

  private void createHashes (List<String> nodes) {
    if (nodes.size() == 1) {
      return ;
    }

    List<String> treeLevel = new ArrayList<>();
    for (int i = 0; i < nodes.size(); i += 2) {
      String left = nodes.get(i);
      String right = nodes.get(i + 1);
      String data = left + right;
      treeLevel.add(Hash.sha3(data).substring(2));
    }

    // is odd number of nodes
    if (nodes.size() % 2 == 1) {
      treeLevel.add(nodes.get(nodes.size() - 1));
    }

    layers.add(treeLevel);
    createHashes(treeLevel);
  }

  /**
   * @Description get root
   * @return  root
   */
  public String getRoot() {
    int depth = layers.size();
    return layers.get(depth - 1).get(0);
  }

  /**
   * @Description get all leaf nodes
   * @return  leaves
   */
  public List<String> getLeaves() {
    return leaves;
  }

  List<String> getProof(String leaf) {
    int index = -1;
    for (int i = 0; i < this.leaves.size(); i++) {
      if (leaf.equals(this.leaves.get(i))) {
        index = i;
      }
    }

    List<String> proof = new ArrayList<>();
    if (index <= this.leaves.size()) {
      int siblingIndex;
      for (int i = 0; i < this.layers.size() - 1; i++) {
        if (index % 2 == 0) {
          siblingIndex = index + 1;
        } else {
          siblingIndex = index - 1;
        }
        index = index / 2;
        proof.add(this.layers.get(i).get(siblingIndex));
      }
    }
    return proof;
  }

  public boolean verify(String value, int index, String root, List<String> proof) {
    if (CollectionUtils.isEmpty(proof) || StringUtils.isBlank(value) || StringUtils.isBlank(root)) {
      return false;
    }

    String hash = value;
    for (int i = 0; i < proof.size(); i++) {
      String node = proof.get(i);
      String hashIn = null;
      if (index % 2 == 0) {
        hashIn = node.startsWith("0x") ? hash + node.substring(2) : hash + node;
      } else {
        hashIn = hash.startsWith("0x") ? node + hash.substring(2) : node + hash;
      }
      hash = Hash.sha3(hashIn);
      index = index / 2;
    }

    return hash.equals(root);
  }
}
