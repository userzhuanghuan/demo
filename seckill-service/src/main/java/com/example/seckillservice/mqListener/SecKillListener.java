package com.example.seckillservice.mqListener;

import com.alibaba.fastjson.JSONObject;
import com.example.secKill.model.Orders;
import com.example.secKill.service.OrdersService;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class SecKillListener {
    @Resource
    private OrdersService ordersService;
    @JmsListener(destination = "seckillQueue")
    public void secKillOrderMQ(String message){
        //生成订单
        Orders orders= JSONObject.parseObject(message, Orders.class);
       ordersService.addOrders(orders);
    }
}
