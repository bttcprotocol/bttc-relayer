#
# relayer
# Copyright (c) 2023-present, bttc.

CREATE database if NOT EXISTS `bttc_relayer` default character set utf8mb4 collate utf8mb4_general_ci;
use `bttc_relayer`;

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

INSERT INTO `token_map` (id, token_name,token_desc,chain_id,main_address,child_address,status,token_precision,symbol_main,name_main,swap_flag) VALUES (1,'BTT','BitTorrent',1,'TAFjULxiVgT4qWk6UZwjqwZXTSaGaqnVp4','0x0000000000000000000000000000000000001010',0,18,'BTT','BitTorrent',1),(2,'TRX','TRX',1,'TZDXJyYhSjM8T4cUYqGj2yib718E7ZmGQc','0xEdf53026aeA60f8F75FcA25f8830b7e2d6200662',0,6,'TRX','TRON',4),(3,'BTT_e','BitTorrent_Ethereum',2,'0xC669928185DbCE49d2230CC9B0979BE6DC797957','0x65676055e58b02e61272cedec6e5c6d56badfb86',0,18,'BTT','BitTorrent',2),(4,'BTT_b','BitTorrent_BSC',3,'0x352cb5e19b12fc216548a2677bd0fce83bae434b','0xcbb9edf6775e39748ea6483a7fa6a385cd7e9a4e',0,18,'BTT','BitTorrent',3),(5,'ETH','ETH',2,'0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE','0x1249C65AfB11D179FFB3CE7D4eEDd1D9b98AD006',0,18,'ETH','Ethereum',0),(6,'BNB','BNB',3,'0xff00000000000000000000000000000000000002','0x185a4091027E2dB459a2433F85f894dC3013aeB5',0,18,'BNB','BNB',0),(8,'USDT_e','Tether USD_Ethereum',2,'0xdac17f958d2ee523a2206206994597c13d831ec7','0xE887512ab8BC60BcC9224e1c3b5Be68E26048B8B',0,6,'USDT','Tether USD',0),(9,'USDT_b','Binance-Peg BSC-USD_BSC',3,'0x55d398326f99059ff775485246999027b3197955','0x9B5F27f6ea9bBD753ce3793a07CbA3C74644330d',0,18,'USDT','Binance-Peg BSC-USD',0),(11,'USDT_t','Tether USD_TRON',1,'TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t','0xdB28719F7f938507dBfe4f0eAe55668903D34a15',0,6,'USDT','Tether USD',0),(13,'WIN_t','WINkLink_TRON',1,'TLa2f6VPqDgRE67v1736s7bJ8Ray5wYjU7','0xD36CA9a2EfFF2ec05Ed016d7cD28E38659D7D09b',0,6,'WIN','WINkLink',13),(14,'NFT_t','APENFT_TRON',1,'TFczxzPhnThNSqr5by8tvxsdCFRRz6cPNq','0x89a93F94C0a3f388930C4A568430F5e8fFFfd3eC',0,6,'NFT','APENFT',16),(15,'TUSD_e','TrueUSD_Ethereum',2,'0x0000000000085d4780B73119b644AE5ecd22b376','0xE7424Ab0E1828d83ad402DA5644142E55598c782',0,18,'TUSD','TrueUSD',0),(16,'TUSD_b','TrueUSD_BSC',3,'0x14016e85a25aeb13065688cafb43044c2ef86784','0xA2611F4488C92e1A91Eb4D2a8D30110ebA9925b5',0,18,'TUSD','TrueUSD',0),(17,'TUSD_t','TrueUSD_TRON',1,'TUpMhErZL2fhh4sVNULAbNKLokS4GjC1F4','0x04b0F8bd78D07bE6D27209163a5174B4B4ec0fbd',0,18,'TUSD','TrueUSD',0),(18,'SUN_t','SUN_TRON',1,'TSSMHYeV2uE9qYH95DqyoCuNCzEL1NvU3S','0x76AcCFB75B8Bb7c6c295f04d19c1D184D274c853',0,18,'SUN','SUN',7),(19,'JST_t','JUST_TRON',1,'TCFLL5dx5ZJdKnWuesXxi1VPwjLVmWZZy9','0x17501034Df227D8565A8c11F41df2418F5d403B6',0,18,'JST','JUST',10),(20,'USDC_t','USD Coin_TRON',1,'TEkxiTehnzSmSe2XqrBj4w32RUN966rdz8','0x935faA2FCec6Ab81265B301a30467Bbc804b43d3',0,6,'USDC','USD Coin',0),(21,'USDC_e','USD Coin_Ethereum',2,'0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48','0xAE17940943BA9440540940DB0F1877f101D39e8b',0,6,'USDC','USD Coin',0),(22,'USDC_b','USD Coin_BSC',3,'0x8ac76a51cc950d9822d68b83fe1ad97b32cd580d','0xCa424b845497f7204D9301bd13Ff87C0E2e86FCF',0,18,'USDC','Binance-Peg USD Coin',0),(23,'USDJ_t','JUST Stablecoin_TRON',1,'TMwFHYXLJaRUPeW6421aqXL4ZEzPRFGkGT','0xDd85D7bDD2B35A38f527f73Bad1acb09b7A1e35C',0,18,'USDJ','JUST Stablecoin',0),(24,'WBTC_e','Wrapped BTC_Ethereum',2,'0x2260fac5e5542a773aa44fbcfedf7c193bc2c599','0x9888221fE6B5A2ad4cE7266c7826D2AD74D40CcF',0,8,'WBTC','Wrapped BTC',0),(25,'UNI_e','Uniswap_Ethereum',2,'0x1f9840a85d5af5bf1d1762f925bdaddc4201f984','0xE86c326E9A97C3fb6086d22Ca396013D62Bfecca',0,18,'UNI','Uniswap',0),(26,'LINK_e','ChainLink_Ethereum',2,'0x514910771af9ca656af840dff83e8264ecf986ca','0xfd3b093aB6bD4F40810f19e5fF822ac8Cc7e3184',0,18,'LINK','ChainLink',0),(27,'MATIC_e','Matic Token_Ethereum',2,'0x7d1afa7b718fb893db30a3abc0cfc608aacfebb0','0x39A2ec2E3570aA234e49ffEC96F0684a352e3E0E',0,18,'MATIC','Matic Token',0),(28,'FTT_e','FTX Token_Ethereum',2,'0x50d1c9771902476076ecfc8b2a83ad6b9355a4c9','0x01eF4875f33992617775800a0AFC4b087baE808A',0,18,'FTT','FTX Token',0),(29,'DAI_e','Dai Stablecoin_Ethereum',2,'0x6b175474e89094c44da98b954eedeac495271d0f','0xe7dC549AE8DB61BDE71F22097BEcc8dB542cA100',0,18,'DAI','Dai Stablecoin',0),(30,'BUSD_e','Binance USD_Ethereum',2,'0x4fabb145d64652a948d72533023f6e7a623c7c53','0x6E626c62D54554737697E633eF9578ac3C4Ba4Ea',0,18,'BUSD','Binance USD',0),(31,'BTC_b','BTC Token_BSC',3,'0x7130d2a12b9bcbfae4f2634d864a1ee1ce3ead9c','0x1A7019909B10cdD2D8B0034293AD729f1C1F604e',0,18,'BTC','Binance-Peg BTCB Token',0),(32,'ETH_b','Ethereum Token_BSC',3,'0x2170ed0880ac9a755fd29b2688956bd959f933f8','0xA20dfb01DCa223c0E52B0D4991D4aFA7E08e3a50',0,18,'ETH','Binance-Peg Ethereum Token',0),(33,'BUSD_b','BUSD Token_BSC',3,'0xe9e7cea3dedca5984780bafc599bd69add087d56','0xde47772Ac041A4Ccf3c865632131D1093e51C02d',0,18,'BUSD','Binance-Peg BUSD Token',0),(34,'QNT_e','Quant_Ethereum',2,'0x4a220e6096b25eadb88358cb44068a3248254675','0x0ed0cA87b19205daDF17CC77922e2eD8B9542E1a',0,18,'QNT','Quant',0),(35,'DOT_b','Polkadot Token_BSC',3,'0x7083609fce4d1d8dc0c979aab8c869ea2c873402','0x352Cb5E19b12FC216548a2677bD0fce83BaE434B',0,18,'DOT','Binance-Peg Polkadot Tokenn',0),(36,'ADA_b','Cardano Token_BSC',3,'0x3ee2200efb3400fabb9aacf31297cbdd1d435d47','0x43559B1786C06d6B826e3cf9AA667eD8840f9106',0,18,'ADA','Binance-Peg Cardano Token',0),(37,'AAVE_e','Aave Token_Ethereum',2,'0x7fc66500c84a76ad7e9c93437bfc5ac33e2ddae9','0xEEca56f73594B8C8eACE49044Be41EE321A6523F',0,18,'AAVE','Aave Token',0),(38,'AXS_e','Axie Infinity_Ethereum',2,'0xbb0e17ef65f82ab018d8edd776e8dd940327b28b','0xbD1D93eD87cdC4FdfC8Ec1baAc3d1097f77ef6c9',0,18,'AXS','Axie Infinity',0),(39,'BCH_b','Bitcoin Cash Token_BSC',3,'0x8ff795a6f4d97e7887c79bea79aba5cc76444adf','0xb8E7F4A9c72F0c8a6611A2D5a6887db8753AB38a',0,18,'BCH','Binance-Peg Bitcoin Cash Token',0),(40,'LTC_b','Litecoin Token_BSC',3,'0x4338665cbb7b2485a8855a139b75d5e34ab0db94','0xE9122fb2ae79709beaE4a6D41B06cD4EC9ed932d',0,18,'LTC','Binance-Peg Litecoin Token',0),(41,'UST_e','Wrapped UST Token_Ethereum',2,'0xa47c8bf37f92aBed4A126BDA807A7b7498661acD','0xEb7121Ea70e95c7141c58f15e79f89c5CFB9ACbc',0,18,'USTC','Wrapped UST Token',0),(42,'SHIB_e','SHIBA INU_Ethereum',2,'0x95ad61b0a150d79219dcf64e1e6cc01f0b64c4ce','0x26290429aC8839cB7B16AA283DAb359b43E574Eb',0,18,'SHIB','SHIBA INU',0),(43,'OKB_e','OKB_Ethereum',2,'0x75231f58b43240c9718dd58b4967c5114342a86c','0xAd1e3a27c50E01cC2d51b64f546621e5ff138C93',0,18,'OKB','OKB',0),(44,'XRP_b','XRP Token_BSC',3,'0x1d2f0da169ceb9fc7b3144628db156f3f6c60dbe','0xF5DbB4e26C1946DFd5aD8cf2e1Cbda3510721bB8',0,18,'XRP','Binance-Peg XRP Token',0),(45,'AVAX_b','Avalanche Token_BSC',3,'0x1ce0c2827e2ef14d5c4f29a091d735a204794041','0x4CC2FC7373B491db83bE3feB5db622e0773420cf',0,18,'AVAX','Binance-Peg Avalanche Token',0),(46,'GRT_e','Graph Token_Ethereum',2,'0xc944e90c64b2c07662a292be6244bdf05cda44a7','0x2df7f3f1B413e9ACc2140D19257A37b4E79Fd163',0,18,'GRT','Graph Token',0),(47,'FTM_e','Fantom Token_Ethereum',2,'0x4e15361fd6b4bb609fa63c81a2be19d873717870','0xAD9A21FF0c9d854cA8C1360AF28D4fcbDaC53B4F',0,18,'FTM','Fantom Token',0),(48,'CAKE_b','PancakeSwap Token_BSC',3,'0x0e09fabb73bd3ade0a17ecc321fd13a19e81ce82','0x2Eb6d639621Da30f61458D160338cd90547aDe9f',0,18,'CAKE','PancakeSwap Token',0),(49,'XVS_b','VENUS_BSC',3,'0xcf6bb5389c92bdda8a3747ddb454cb7a64626c63','0xdf38Cf33948418647e6c068f5aec7A1BBa5c3bDd',0,18,'XVS','VENUS',0),(50,'SUSHI_e','SushiToken_Ethereum',2,'0x6b3595068778dd592e39a122f4f5a5cf09c90fe2','0x53C56ece35f8CaB135e13D6d00499Dfc7c07A92e',0,18,'SUSHI','SushiToken',0),(51,'MKR_e','Maker_Ethereum',2,'0x9f8f72aa9304c8b593d555f12ef6589cc3a579a2','0xdd038Fe9495488C51Fe482Ae25bFe5241bE974BE',0,18,'MKR','Maker',0),(52,'COMP_e','Compound_Ethereum',2,'0xc00e94cb662c3520282e6f5717214004a7f26888','0xf0BacE186818844758c89b4b2b252d3918781A8a',0,18,'COMP','Compound',0),(53,'CRV_e','Curve DAO Token_Ethereum',2,'0xD533a949740bb3306d119CC777fa900bA034cd52','0x2E5c72F567dA40d0021Fb9CaDB6980c86c3A8Aa7',0,18,'CRV','Curve DAO Token',0),(54,'BAKE_b','BakeryToken_BSC',3,'0xE02dF9e3e622DeBdD69fb838bB799E3F168902c5','0xb817b1ACf8568c8AA28667ea643C31CAF7a29DFd',0,18,'BAKE','BakeryToken',0),(55,'TWT_b','Trust Wallet_BSC',3,'0x4b0f1812e5df2a09796481ff14017e6005508003','0x1e0c6878F875Da634F19c8eb989f416F5c759252',0,18,'TWT','Trust Wallet',0),(56,'BUNNY_b','Bunny Token_BSC',3,'0xc9849e6fdb743d08faee3e34dd2d1bc69ea11a51','0x4bF20082a4e60DC3D48808E98662181C701E438D',0,18,'BUNNY','Bunny Token',0),(57,'REEF_b','Reef.finance_BSC',3,'0xf21768ccbc73ea5b6fd3c687208a7c2def2d966e','0xadF3e70A219B8C589B76AEB594ad8aFeE6F4e372',0,18,'REEF','Reef.finance',0),(58,'AUTO_b','AUTOv2_BSC',3,'0xa184088a740c695e156f91f5cc086a06bb78b827','0x202df24147b5117419877B54Fd66D2bba0df7661',0,18,'AUTO','AUTOv2',0),(59,'ALPACA_b','AlpacaToken_BSC',3,'0x8f0528ce5ef7b51152a59745befdd91d97091d2f','0x56dd25296E5124b1eaB55244F312d024Aaf31f22',0,18,'ALPACA','AlpacaToken',0),(60,'BURGER_b','Burger Swap_BSC',3,'0xae9269f27437f0fcbc232d39ec814844a51d6b8f','0x1AA43AdE447976518B8B3850b25071E4893DF397',0,18,'BURGER','Burger Swap',0),(61,'TRX_e','TRON_Ethereum',2,'0x50327c6c5a14DCaDE707ABad2E27eB517df87AB5','0x5BaE3D53484848A4a15607b2490d51B484BF42dc',0,6,'TRX','TRON',5),(62,'SUN_e','SUN_Ethereum',2,'0xF6a36a5A942dec8bb60E5CBf005D70D89aBFB505','0x13990E637E74EC35fa6aEfCCD0292ee5Fa83738D',0,18,'SUN','SUN',8),(63,'JST_e','JUST_Ethereum',2,'0x3bCd95DAc783C2B60c34dbC77a3E7FA1572B8fd0','0x6c0a243302429D3aB54207414aEaBB7c6bE70aEb',0,18,'JST','JUST',11),(64,'WIN_e','WINkLink_Ethereum',2,'0x5ac99d6172247D5319c72B2c95E3B9D6fe9895d0','0xf93d4Af9caF6f9995BCc347eB7a1A93424B4723A',0,6,'WIN','WINkLink',14),(65,'NFT_e','APENFT_Ethereum',2,'0x198d14F2Ad9CE69E76ea330B374DE4957C3F850a','0x0E0c0f3df7F7989272e63a459483dd86C4A9a943',0,6,'NFT','APENFT',17),(66,'TRX_b','TRON_BSC',3,'0xCE7de646e7208a4Ef112cb6ed5038FA6cC6b12e3','0xdC3e2D18B6b1b5Db09ECDB7f6C8B8Fa01564236f',0,6,'TRX','TRON',6),(67,'SUN_b','SUN_BSC',3,'0x3b1377d50DDb4609536beA482f41a2E1A6C4e857','0xcFb48B550e4D864766e9AEfe6b104871972a3b34',0,18,'SUN','SUN',9),(68,'JST_b','JUST_BSC',3,'0xCEdE21A4D3e8Afe51bb353D7C2e67543232fE0aD','0x4c6df5AdECc64A35C13863Daab59b886cc1c780c',0,18,'JST','JUST',12),(69,'WIN_b','WINkLink_BSC',3,'0x056d5e4e7D47b8703Bc3bD46d17aA3D37420578f','0x366C21BFfbb2e60AadF8a1D1D6E0c45096f07D9f',0,6,'WIN','WINkLink',15),(70,'NFT_b','APENFT_BSC',3,'0x20eE7B720f4E4c4FFcB00C4065cdae55271aECCa','0x01910c7821f7652fedeb21334A9e256059084579',0,6,'NFT','APENFT',18),(71,'ICR_t','InterCrone_TRON',1,'TKqvrVG7a2zJvQ3VysLoiz9ijuMNDehwy7','0x096c64d79a85c8fd2e963c4abd9373301d2cf801',0,8,'ICR','InterCrone',0),(72,'ICR_b','InterCrone',3,'0x4f60Ad2c684296458b12053c0EF402e162971e00','0x252CD063341a1A47933086b93f85417C09C54aec',0,8,'ICR','InterCrone',0),(73,'KNC_b','Kyber Network Crystal v2 - BSC',3,'0xfe56d5892BDffC7BF58f2E84BE1b2C32D21C308b','0x18fA72e0EE4C580a129b0CE5bD0694d716C7443E',0,18,'KNC','Binance-Peg Kyber Network Crystal Token',0),(74,'KNC_e','Kyber Network Crystal v2 - Ethereum',2,'0xdeFA4e8a7bcBA345F687a2f1456F5Edd9CE97202','0xE467F79E9869757DD818DfB8535068120F6BcB97',0,18,'KNC','Kyber Network Crystal v2',0),(75,'USDD_t','Decentralized USD_TRON',1,'TPYmHEhy5n8TCEfYGqW2rPxsghSfzghPDn','0x17f235fd5974318e4e2a5e37919a209f7c37a6d1',0,18,'USDD','Decentralized USD',19),(76,'USDD_e','Decentralized USD_Ethereum',2,'0x0c10bf8fcb7bf5412187a595ab97a3609160b5c6','0xb602f26bf29b83e4e1595244000e0111a9d39f62',0,18,'USDD','Decentralized USD',20),(77,'USDD_b','Decentralized USD_BSC',3,'0xd17479997f34dd9156deef8f95a52d81d265be9c','0x74e7cef747db9c8752874321ba8b26119ef70c9e',0,18,'USDD','Decentralized USD',21),
(78,'FOR_b','The Force Token_BSC',3,'0x658A109C5900BC6d2357c87549B651670E5b0539','0xCccf2D47a29E64B3b3BFD9e9321f71E789179A6C',0,18,'FOR','The Force Token',0),('79', 'MDT_t', 'Measurable Data Token_TRON', 1, 'TVoKC3ZhU7rcisPRFgunNwHVmSqfmiTB9m', '0x41bf1b203f9deBd260063d1967D93a2A7bF5509a', '0', '18', 'MDT', 'Measurable Data Token', '22'),('80', 'MDT_e', 'Measurable Data Token_Ethereum', 2, '0x814e0908b12A99FeCf5BC101bB5d0b8B5cDf7d26', '0x3F171f7Fb831a462f7Bd2e1439F7eF1BE9BCDF98', '0', '18', 'MDT', 'Measurable Data Token', '23');
INSERT INTO event_url_config VALUES (10, 'eth', 'https://eth.trongrid.io'),(11, 'bsc', 'https://bsc.trongrid.io');
commit;