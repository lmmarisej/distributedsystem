package com.sample.mall.goods.service.impl;


import com.sample.mall.common.base.Constants;
import com.sample.mall.common.base.ResponseEnum;
import com.sample.mall.common.dto.GoodsDTO;
import com.sample.mall.common.dto.OrderItemDTO;
import com.sample.mall.common.exception.BusinessException;
import com.sample.mall.common.util.Assert;
import com.sample.mall.common.util.JSONUtil;
import com.sample.mall.common.util.ObjectTransformer;
import com.sample.mall.goods.mapper.GoodsMapper;
import com.sample.mall.goods.model.GoodsDO;
import com.sample.mall.goods.service.IGoodsService;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import redis.clients.jedis.Jedis;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GoodsServiceImpl implements IGoodsService {

    private static final Logger logger = LoggerFactory.getLogger(GoodsServiceImpl.class);

    @Resource
    GoodsMapper goodsMapper;

    @Override
    public boolean addGoods(GoodsDTO goodsDTO) {
        GoodsDO goodsDO = ObjectTransformer.transform(goodsDTO, GoodsDO.class);
        int result = goodsMapper.insertGoods(goodsDO);
        return Assert.singleRowAffected(result);
    }

    @Override
    public GoodsDTO getGoods(Long id) {
        GoodsDO goodsDO = null;
        try (Jedis jedis = new Jedis("localhost", 6379);) {

            String cacheKey = String.format(Constants.GOODS_CACHE_KEY, id);
            String cacheValue = jedis.get(cacheKey);
            logger.info("key:{}, value:{}", cacheKey, cacheValue);

            if(StringUtils.isBlank(cacheValue)) {

                goodsDO = goodsMapper.selectGoodsById(id);
                Assert.notNull(goodsDO);

                jedis.set(cacheKey, JSONUtil.toJSONString(goodsDO));
            } else {
                goodsDO = JSONUtil.parseObject(cacheValue, GoodsDO.class);
            }
        }
        return ObjectTransformer.transform(goodsDO, GoodsDTO.class);
    }

    @Override
    public GoodsDTO getGoodsNameAndPrice(Long id) {
        GoodsDO goodsDO;
        try (Jedis jedis = new Jedis("localhost", 6379);) {

            String cacheKey = String.format(Constants.GOODS_CACHE_KEY, id);
            Map<String, String> cacheValue = jedis.hgetAll(cacheKey);
            logger.info("key:{}, value:{}", cacheKey, cacheValue);

            if(CollectionUtils.isEmpty(cacheValue)) {
                goodsDO = goodsMapper.selectGoodsById(id);
                Assert.notNull(goodsDO);

                cacheValue = new HashMap<>();
                cacheValue.put("name", goodsDO.getName());
                cacheValue.put("price", goodsDO.getPrice().toPlainString());
                jedis.hmset(cacheKey, cacheValue);
            } else {
                goodsDO = new GoodsDO();
                goodsDO.setName(cacheValue.get("name"));
                goodsDO.setPrice(new BigDecimal(cacheValue.get("price")));
            }
        }
        return ObjectTransformer.transform(goodsDO, GoodsDTO.class);
    }

    /**
     * ??????????????????
     *
     * <p>
     * update t_goods
     *    set stock = stock - #{number}
     *  where id = #{goodsId}
     *    and stock >= #{number};
     * </p>
     *
     * ?????????stock >= #{number} <br/>
     * ?????????????????????????????????????????????????????????????????????????????????????????????????????????
     *
     * @param orderItemDTOList
     * @return
     */
    @Override
    public boolean decreaseStock(List<OrderItemDTO> orderItemDTOList) {
        GoodsDO goodsDO;
        List<GoodsDO> goodsDOList = new ArrayList<>();
        for (OrderItemDTO orderItem : orderItemDTOList) {
            goodsDO = new GoodsDO();
            goodsDO.setId(orderItem.getGoodsId());
            // ????????????stock??????????????????????????????
            goodsDO.setStock(orderItem.getNumber());
            goodsDOList.add(goodsDO);
        }
        int result = goodsMapper.updateGoodsListStock(goodsDOList);
        if (result != goodsDOList.size()) {
            // ????????????????????????????????????????????????????????????????????????????????????
            throw new BusinessException(ResponseEnum.GOODS_STOCK_NOT_ENOUGH);
        }
        return true;
    }

}
