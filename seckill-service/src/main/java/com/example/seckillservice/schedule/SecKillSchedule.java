package com.example.seckillservice.schedule;

import com.example.common.CommonsConstants;
import com.example.common.CommonsReturnObject;
import com.example.seckillservice.mapper.GoodsMapper;
import com.example.secKill.model.Goods;
import com.example.seckillservice.mapper.GoodsMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

import javax.annotation.Resource;
import java.util.List;

@EnableScheduling
@Component
public class SecKillSchedule {
    @Resource
    private RedisTemplate<String,String> redisTemplate;
    @Resource
    private GoodsMapper goodsMapper;
    @Scheduled(cron ="0/5 * * * * *")
    public void initSecKillGoods(){
        StringRedisSerializer stringRedisSerializer=new StringRedisSerializer();
        //将redis的键和值都序列化，便于查看
        redisTemplate.setKeySerializer(stringRedisSerializer);
        redisTemplate.setValueSerializer(stringRedisSerializer);
        //获取数据库中即将参与秒杀活动的商品，存入redis中，需要符合时间规则的数据，如每天凌晨0点执行，那么需要将数据库中开始时间大于等于今天，结束时间小于明天的商品数据
        List<Goods> goods=goodsMapper.selectSecKillGoods();

        for(Goods g:goods){

            //使用商品的随机名加上库存前缀作为key存入redis中，如果数据已经存在，那么跳过
            if(redisTemplate.opsForValue().get(CommonsConstants.SECKILL_STORE+g.getRandomName())!=null){
                continue;
            }
            redisTemplate.opsForValue().set(CommonsConstants.SECKILL_STORE+g.getRandomName(),g.getStore()+"");

        }

    }
}
