package org.bttc.relayer.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import javax.annotation.Resource;
import org.bttc.relayer.bean.dao.TokenMap;
import org.bttc.relayer.bean.enums.ChainTypeEnum;
import org.bttc.relayer.mapper.TokenMapMapper;
import org.springframework.stereotype.Service;

@Service
public class TokenMapService  extends ServiceImpl<TokenMapMapper, TokenMap> {

    @Resource
    private TokenMapMapper tokenMapMapper;

    public List<TokenMap> getTokenMaps(int chainId) {
        QueryWrapper<TokenMap> tokenMapQueryWrapper = new QueryWrapper<>();
        tokenMapQueryWrapper.eq("status", 0);
        if (chainId != 0) {
            tokenMapQueryWrapper.eq("chain_id", chainId);
        }
        return tokenMapMapper.selectList(tokenMapQueryWrapper);
    }

    public TokenMap getTokenMap(String tokenId, int chainId) {
        QueryWrapper<TokenMap> tokenMapQueryWrapper = new QueryWrapper<>();
        tokenMapQueryWrapper.eq("status", 0);
        if (chainId != ChainTypeEnum.BTT.code) {
            tokenMapQueryWrapper.eq("chain_id", chainId);
            tokenMapQueryWrapper.eq("main_address", tokenId);
        } else {
            tokenMapQueryWrapper.eq("child_address", tokenId);
        }
        return tokenMapMapper.selectOne(tokenMapQueryWrapper);
    }
}
