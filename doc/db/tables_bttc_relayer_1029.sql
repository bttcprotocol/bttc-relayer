#
# relayer
# Copyright (c) 2023-present, bttc.

CREATE database if NOT EXISTS `bttc_relayer_1029` default character set utf8mb4 collate utf8mb4_general_ci;
use `bttc_relayer_1029`;

SET NAMES utf8mb4;

CREATE TABLE `transactions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `src_txid` varchar(66) NOT NULL COMMENT 'the hash of the bttc chain',
  `dest_txid` varchar(66) DEFAULT NULL COMMENT 'the hash of the main chain',
  `from_address` varchar(66) DEFAULT NULL,
  `to_address` varchar(66) DEFAULT NULL,
  `dest_tx_owner` varchar(66) DEFAULT NULL COMMENT 'the owner of the dest_txid',
  `src_chain_id` int(11) DEFAULT NULL COMMENT 'the id of bttc chain ',
  `dest_chain_id` int(11) DEFAULT NULL COMMENT 'the id of main chain:11-tron,2-eth,3-bsc ',
  `src_token_id` varchar(66) DEFAULT NULL,
  `dest_token_id` varchar(66) DEFAULT NULL,
  `from_amount` varchar(100) DEFAULT NULL,
  `to_amount` varchar(100) DEFAULT NULL,
  `fee` varchar(100) DEFAULT NULL,
  `from_block` bigint(20) DEFAULT NULL COMMENT 'the block number of src_txid',
  `to_block` bigint(20) DEFAULT NULL COMMENT 'the block number of dest_txid',
  `t_status` tinyint(3) unsigned NOT NULL DEFAULT '0' COMMENT 'the status of the transaction',
  `src_contract_ret` varchar(255) DEFAULT NULL COMMENT 'the result of src_txid',
  `dest_contract_ret` varchar(255) DEFAULT NULL COMMENT 'the result of dest_txid',
  `src_timestamp` datetime DEFAULT NULL COMMENT 'the timestamp of src_txid',
  `dest_timestamp` datetime DEFAULT NULL COMMENT 'the timestamp of dest_txid',
  `nonce` bigint(20) DEFAULT NULL COMMENT 'nonce of dest_txid',
    `content` text DEFAULT NULL ,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `index_src_txid` (`src_txid`) USING BTREE,
  KEY `index_to` (`to_address`) USING BTREE,
  KEY `index_status` (`t_status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `token_map` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `token_name` varchar(16) NOT NULL COMMENT 'token name',
  `token_desc` varchar(255) DEFAULT NULL COMMENT 'token description',
  `chain_id` int(11) NOT NULL COMMENT 'main chain ID',
  `main_address` varchar(127) NOT NULL COMMENT 'token address on main chain',
  `child_address` varchar(127) NOT NULL COMMENT 'token address on child chain',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'data status,0:active 1:deleted',
  `token_precision` int(11) DEFAULT NULL,
  `symbol_main` varchar(16) NOT NULL DEFAULT '' COMMENT 'main chain token symbol',
  `name_main` varchar(255) NOT NULL DEFAULT '' COMMENT 'main chain token name',
  `swap_flag` int(11) NOT NULL DEFAULT '0',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create date',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `idx_name` (`token_name`,`chain_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `check_point_info` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `check_point_num` bigint(20) NOT NULL,
  `tx_id` varchar(66) NOT NULL COMMENT 'the hash of submitting this checkpoint',
  `start_block` bigint(20) NOT NULL COMMENT 'the start block of this checkpoint',
  `end_block` bigint(20) NOT NULL COMMENT 'the end block of this checkpoint',
  `block_number` bigint(20) NOT NULL COMMENT 'the block number of the checkpoint tx',
  `chain_id` int(11) NOT NULL COMMENT 'the chain id of the checkpoint tx',
  `result` varchar(255) NOT NULL,
  `confirm` tinyint(4) NOT NULL COMMENT 'if this tx confirmed:0-false, 1-true',
  `time_stamp` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `unique_tx_chain_index` (`tx_id`,`chain_id`) USING BTREE,
  KEY `check_point_num_index` (`check_point_num`) USING BTREE,
  KEY `start_block_index` (`start_block`) USING BTREE,
  KEY `end_block_index` (`end_block`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `message_center_config` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `chain` varchar(32) NOT NULL,
  `contract_address` varchar(64) NOT NULL,
  `max_confirm_block` mediumtext NOT NULL,
  `max_unconfirm_block` mediumtext NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `unique_chain_contract_address` (`chain`,`contract_address`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `event_url_config` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `chain` varchar(32) NOT NULL,
  `url` varchar(100) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `token_map` (id, token_name,token_desc,chain_id,main_address,child_address,status,token_precision,symbol_main,name_main,swap_flag) VALUES (1,'BTT','Btt Token',1,'TVSvjZdyDSNocHm7dP3jvCmMNsCnMTPa5W','0x0000000000000000000000000000000000001010',0,18,'BTT','BitTorrent',1),(2,'TRX','TRX_TRON',1,'TZDXJyYhSjM8T4cUYqGj2yib718E7ZmGQc','0x8e009872b8a6d469939139be5e3bbd99a731212f',0,6,'TRX','TRON',4),(3,'ETH','ETH_Ethereum',2,'0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE','0x12196ecbcc63a4e61f6907535097990494e3d215',0,18,'ETH','Ethereum',0),(4,'BTT_e','BitTorrent_Ethereum',2,'0xA0Fbd0cDDdE9fb2F91327f053448a0F3319552F7','0xd9dcae335acd3d4ffd2e6915dc702a59136ab46f',0,18,'BTT','BitTorrent',2),(5,'BNB','Binance Coin_BSC',3,'0xff00000000000000000000000000000000000002','0x61a17734e6b497cba51f16ae23bcf85aeb6984cb',0,18,'BNB','Binance Coin',0),(6,'BTT_b','BitTorrent_BSC',3,'0x01F81a28f4ADab7fE7D5Cf33dEC0CA40e29c0c8a','0xdf83e6ef1b1db73718d9a3c35380033b1b1915c3',0,18,'BTT','BitTorrent',3),(7,'USDT_t','Tether USD_TRON',1,'TXLAQ63Xg1NAzckPwKHvzw7CSEmLMEqcdj','0x7b906030735435422675e0679bc02dae7dfc71da',0,6,'USDT','Tether USD',0),(8,'USDT_e','Tether USD_Ethereum',2,'0x3C843344c3C5853189F3d92b3E6C6E46360163F9','0x298fa36a9e2ebd6d3698e552987294fa8b65cd00',0,6,'USDT','Tether USD',0),(9,'USDT_b','Tether USD_BSC',3,'0xb8206839fF8e6CF52c52FD613F28F37Ac40d1F5f','0x834982c9b0690ed7ca35e10b18887c26c25cdc82',0,6,'USDT','Tether USD',0),(10,'SUN_t','SUN_TRON',1,'TDqjTkZ63yHB19w2n7vPm2qAkLHwn9fKKk','0xB1DD5E7556D90C0589d56c144DF39337D82a1fEA',0,18,'SUN','SUN',7),(11,'JST_t','JUST_TRON',1,'TF17BgPaZYbz8oxbjhriubPDsA7ArKoLX3','0x593ccDAb8Cd2A21467e4D27241CE999AB8DE83Bc',0,18,'JST','JUST',10),(12,'WIN_t','WINkLink_TRON',1,'TNDSHKGBmgRx9mDYA9CnxPx55nu672yQw2','0x52a4942848AF737A5ac71Bf23bf093Dc64548D36',0,6,'WIN','WINkLink',13),(13,'TRX_e','TRON_Ethereum',2,'0xBa938dA1Abb5f8646F2b7DC22E3700950B996d78','0x126e6Fc28b9A4b31b51E78801216b09BEED5B4C0',0,6,'TRX','TRON',5),(14,'SUN_e','SUN_Ethereum',2,'0x1d3b00a4987363620877987D5fF6d5cCF872BB6c','0x2F05aC16A299d77aB2566A9e7F40c25a2a5b0c03',0,18,'SUN','SUN',8),(15,'JST_e','JUST_Ethereum',2,'0x78FBEf93f91b9366EA2e33063B4985a993AbD2Cc','0x73b35988F8B625CaF2C0475996E8EEb99B99d026',0,18,'JST','JUST',11),(16,'WIN_e','WINkLink_Ethereum',2,'0x4606E2228a8bc2F6C697020e04282fDf62739Bf6','0x592Fedee620C1C0eae56C44C79C4A5a69c8220bE',0,6,'WIN','WINkLink',14),(17,'NFT_e','APENFT_Ethereum',2,'0x95c4D915743b262A9bF56A70632c9872DCd02336','0x51132b16f526B2798b7A5ee8427480B9B430b2b6',0,6,'NFT','APENFT',0),(18,'TRX_b','TRON_BSC',3,'0x0f939Bf36A3533D42B327002d7Bc9CfFa3D59C8C','0xa20cfBff5b21c1CD64552C7BAbCeCE4a336088eF',0,6,'TRX','TRON',6),(19,'SUN_b','SUN_BSC',3,'0xce4B348c87802B8E3298444b30ad4aA5bbFCc13F','0x4e9446B6c98CBe38A77c31ff4413fD58d08b2a9D',0,18,'SUN','SUN',9),(20,'JST_b','JUST_BSC',3,'0x7384848ca538f03fCe1DC74A414985D20d20Bf73','0x2780f8F71db5bEb7E7F9a9ADf592437E5C0c8bB4',0,18,'JST','JUST',12),(21,'WIN_b','WINkLink_BSC',3,'0x702Dd76Cbf3c659921d9D6b32cf59c9CE187778D','0xB386deEc7cf7E2021A5873d2F48b4A3DAcDa083F',0,6,'WIN','WINkLink',15),(22,'NFT_b','APENFT_BSC',3,'0x8Ee458FBb90C81066345d7ee9B65ABfc8DEd4093','0x7a5DA577445729fe88D12F51Ef8C1d0B022d8B57',0,6,'NFT','APENFT',0),(23,'USDD_t','Decentralized USD_TRON',1,'TMe9WF7ewAHsNutNKRe2BRFsZzZqqx5sp6','0xa092706717Dcb6892b93f0baAcC07B902Dbd509C',0,18,'USDD','Decentralized USD',16),(24,'USDD_e','Decentralized USD_ETH',2,'0x28B03D12B08fAF4334b73Dbc85A03F5A9cAFe957','0xb2Af0952703cA1eaBa838Af01448a19A72CA02E6',0,18,'USDD','Decentralized USD',17),(25,'USDD_b','Decentralized USD_BSC',3,'0xE2A55cb659B9b03660e3aFD67C48F0E5ae3154D6','0x88d81A35E07C72c01fbe14ce775941cb43231f84',0,18,'USDD','Decentralized USD',18);
INSERT INTO `event_url_config` VALUES (10, 'eth', 'https://eth-goerli.trongrid.io'), (11, 'bsc', 'https://testnet-bsc.trongrid.io');
commit;

