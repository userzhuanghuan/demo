package com.example.seckillservice.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSONObject;
import com.example.common.CommonsConstants;
import com.example.common.CommonsReturnObject;
import com.example.common.CommonsReturnObject;
import com.example.secKill.model.Goods;
import com.example.secKill.model.Orders;
import com.example.secKill.service.GoodsService;
import com.example.seckillservice.mapper.GoodsMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service(interfaceClass = GoodsService.class)
@Component("goodsService")
public class GoodsServiceImpl implements GoodsService {
    @Resource
    private GoodsMapper goodsMapper;
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Resource
    private JmsTemplate jmsTemplate;

    @Override
    public List<Goods> queryAll() {
        return goodsMapper.selectAll();
    }

    @Override
    public Goods queryGoodsById(Integer id) {
        return goodsMapper.selectByPrimaryKey(id);
    }

    /**
     * 秒杀的业务方法，这里我们需要完成秒杀的所有业务逻辑实现
     *
     * @param uid
     * @param goodsId
     * @param randomName
     * @return
     */
    public CommonsReturnObject secKill(Integer uid, Integer goodsId, String randomName) {
        System.out.println("开始");
        //设置Redis中的key和value以字符串的格式存放数据，否则存放的内容可能会是乱的
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        redisTemplate.setValueSerializer(stringRedisSerializer);
        redisTemplate.setKeySerializer(stringRedisSerializer);
        CommonsReturnObject returnObject = new CommonsReturnObject();
        //验证秒杀商品库存是否充足
        String store = redisTemplate.opsForValue().get(CommonsConstants.SECKILL_STORE + randomName);
        //如果store为null，表示商品不存在
        if (null == store) {
            returnObject.setData(null);
            returnObject.setCode(CommonsConstants.ERROR);
            returnObject.setErrorMessage("对不起，该商品不在此次秒杀活动范围中，请静等下次活动");
            return returnObject;
        }
        //如果库存数不足
        if (Integer.valueOf(store) <= 0) {
            returnObject.setData(null);
            returnObject.setCode(CommonsConstants.ERROR);
            returnObject.setErrorMessage("对不起，该商品已售完，请静等下次活动");
            System.out.println("对不起，该商品已售完，请静等下次活动");
            return returnObject;
        }
        //验证用户是否已经购买过了（每个用户同一种商品限购一件）
        String str = redisTemplate.opsForValue().get(CommonsConstants.HANDLE_USER + randomName + uid);
        //如果str不为null，那么说明该用户已经购买过此商品
        if (null != str) {
            returnObject.setData(null);
            returnObject.setCode(CommonsConstants.ERROR);
            returnObject.setErrorMessage("对不起，您已经购买过此商品，请静等下次活动");
            return returnObject;
        }
        //限流，限流可以防止请求过多进入服务导致服务器不可用，限流可以使用一个固定的值例如1000或10000等等，也可利用商品的剩余数量*某个固定的倍数控制
        synchronized ("1") {
            String strlimit = redisTemplate.opsForValue().get(CommonsConstants.LIMITING_LIST);
            //如果访问量超过限制数，限流
            if (null != strlimit && Integer.valueOf(strlimit) > 1000) {
                returnObject.setData(null);
                returnObject.setCode(CommonsConstants.ERROR);
                returnObject.setErrorMessage("对不起，服务器繁忙，请稍后再试");
                return returnObject;
            }
            //让限流人数加1
            redisTemplate.opsForValue().increment(CommonsConstants.LIMITING_LIST);
        }
        //开始秒杀业务
        redisTemplate.setEnableTransactionSupport(true);
        //定义集合并设定需要监控的key，当多线程并发访问时Redis监控的key的数据被其他线程修改了那么当前线程会放弃事务放弃本次修改
        List<String> watchKey = new ArrayList<>();
        //监控商品随机名，防止超卖
        watchKey.add(CommonsConstants.SECKILL_STORE + randomName);
        redisTemplate.watch(watchKey);
        //再次获取商品库存
        store = redisTemplate.opsForValue().get(CommonsConstants.SECKILL_STORE + randomName);
        if (Integer.valueOf(store) > 0) {
            /**
             * 程序执行到这里，表示当前库存还有，当前线程需要去和其他线程争夺资源，
             * 使用匿名内部类完成事务的提交，excute方法执行后会返回一个list集合的对象，
             * 我们不需要关心集合对象中的内容。
             * 我们只需要关心这个集合对象是不是null，如果是null，表面事务执行失败，原因是事务中可能有Redis命令或监控的key值被其他线程修改了
             */
            List result = redisTemplate.execute(new SessionCallback<List>() {
                @Override
                public <K, V> List<Object> execute(RedisOperations<K, V> redisOperations) throws DataAccessException {
                    //开启事务
                    redisOperations.multi();
                    //redis减少库存
                    redisOperations.opsForValue().decrement((K) (CommonsConstants.SECKILL_STORE + randomName));
                    //将用商品随机名和用户id最为key存入Redis中作为限购，使用任意非null的值作为value
                    redisOperations.opsForValue().set((K) (CommonsConstants.HANDLE_USER + randomName + uid), (V) "1");

                    return redisOperations.exec();

                }
            });
            //如果result不是null，即事务执行成功
            if (null != result&& result.size()!=0) {
                //异步下单，提前让用户的请求返回，释放服务器的资源
                //创建订单
                Orders orders = new Orders();
                orders.setBuyNum(1);
                orders.setGoodsId(goodsId);
                orders.setUid(uid);
                //将订单对象转换为json数据存入队列，用于订单系统完成下单
                String orderJson = JSONObject.toJSONString(orders);
                jmsTemplate.send(new MessageCreator() {
                    @Override
                    public Message createMessage(Session session) throws JMSException {
                        return session.createTextMessage(orderJson);
                    }
                });
                //减少人流数
                redisTemplate.opsForValue().decrement(CommonsConstants.LIMITING_LIST);
                returnObject.setCode(CommonsConstants.OK);
                returnObject.setErrorMessage(null);
                returnObject.setData(null);
            } else {
                //减少一个人流数
                redisTemplate.opsForValue().decrement(CommonsConstants.LIMITING_LIST);
                //递归调用执行业务方法,执行下一轮抢购
                return secKill(uid, goodsId, randomName);
            }
        } else {
            returnObject.setData(null);
            returnObject.setCode(CommonsConstants.ERROR);
            returnObject.setErrorMessage("对不起，该商品已售完，请静等下次活动");
            return returnObject;
        }

        return returnObject;
    }

}
