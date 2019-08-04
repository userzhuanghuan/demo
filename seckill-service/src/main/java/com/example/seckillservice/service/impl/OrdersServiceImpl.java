package com.example.seckillservice.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSONObject;
import com.example.common.CommonsConstants;
import com.example.common.CommonsReturnObject;
import com.example.seckillservice.mapper.GoodsMapper;
import com.example.secKill.model.Goods;
import com.example.secKill.model.Orders;
import com.example.secKill.service.OrdersService;
import com.example.seckillservice.mapper.OrdersMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;

@Service(interfaceClass = OrdersService.class)
@Component("ordersService")
public class OrdersServiceImpl implements OrdersService {
    @Resource
    private OrdersMapper ordersMapper;
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Resource
    private GoodsMapper goodsMapper;

    @Transactional
    @Override
    public void addOrders(Orders orders) {
        Goods goods = goodsMapper.selectByPrimaryKey(orders.getGoodsId());
        orders.setBuyPrice(goods.getPrice());
        orders.setCreateTime(new Date());
        orders.setOrderMoney(goods.getPrice().multiply(new BigDecimal(orders.getBuyNum())));
        orders.setStatus(1);
        ordersMapper.insertOrders(orders);
        redisTemplate.opsForValue().set(CommonsConstants.ORDER_RESULT + orders.getGoodsId() + orders.getUid(), JSONObject.toJSONString(orders));
    }

    @Override
    public Orders getOrdersResult(Integer uid, Integer goodsId) {
        String strOrder = redisTemplate.opsForValue().get(CommonsConstants.ORDER_RESULT + goodsId + uid);
        return strOrder == null ? null : JSONObject.parseObject(strOrder, Orders.class);
    }


}
