package org.bttc.relayer.schedule.strategy;

import org.bttc.relayer.bean.dao.Transactions;

public interface StatusProcessStrategy {
  /**
   * transaction processing function by status
   *
   * @param tx transaction
   */
  @SuppressWarnings("squid:S112")
  void statusProcess(Transactions tx) throws Exception;
}
