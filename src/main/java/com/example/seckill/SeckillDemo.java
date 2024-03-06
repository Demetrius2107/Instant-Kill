package com.example.seckill;


import org.springframework.util.StringUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class SeckillDemo {

      private static final String REDIS_HOST = "127.0.0.1";
      private static final int REDIS_PORT = 6379;
      private static final String REDIS_PASSWORD = "123456";
      private static final int REDIS_TIMEOUT = 10000;
      private static final int REDIS_MAX_TOTAL = 100;
      private static final int REDIS_MAX_IDLE = 10;
      private static final int REDIS_MAX_WAIT_MILLIS = 10000;
      private static final String  STOCK_KEY = "stock";
      private static final String ORDER_QUEUE_KEY = "order_queue";
      private static final String LOCK_KEY = "lock";
      private static JedisPool jedisPool;

      static {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(REDIS_MAX_TOTAL);
        config.setMaxIdle(REDIS_MAX_IDLE);
        config.setMaxWaitMillis(REDIS_MAX_WAIT_MILLIS);
        jedisPool = new JedisPool(config,REDIS_HOST,REDIS_PORT,REDIS_TIMEOUT,REDIS_PASSWORD);
      }

  public static void main(String[] args) {
    Jedis jedis = jedisPool.getResource();

    try {
      //初始化库存数量
      jedis.set(STOCK_KEY, "100");

      //模拟多个用户发起秒杀请求
      for (int i = 0; i < 200; i++) {
        Thread thread = new Thread(new UserThread());
        thread.start();
      }
    } finally {
      if(jedis != null){
        jedis.close();
      }
    }
  }


      static class UserThread implements Runnable{

        public UserThread(){

        }

        /**
         * When an object implementing interface <code>Runnable</code> is used to create a thread,
         * starting the thread causes the object's
         * <code>run</code> method to be called in that separately executing
         * thread.
         * <p>
         * The general contract of the method <code>run</code> is that it may take any action
         * whatsoever.
         *
         * @see Thread#run()
         */
        @Override
        public void run() {
          Jedis jedis = jedisPool.getResource();
          try{

          } catch (Exception e) {
            throw new RuntimeException(e);
          } finally {
            jedis.close();
          }
        }

        private void handleSeckill(Jedis jedis){
          String userId = Thread.currentThread().getName();

          //检查库存数量是否大于0
          String stock = jedis.get(STOCK_KEY);
          int stockNum = StringUtils.hasText(stock) ? 0 : Integer.parseInt(stock);
          if(stockNum <= 0){
            System.out.println(userId + "秒杀失败，库存不足");
            return;
          }
          //将用户的请求加入到队列中
          jedis.lpush(ORDER_QUEUE_KEY,userId);
          //获取分布式锁
          String lockValue = System.currentTimeMillis() + "";
          Long result = jedis.setnx(LOCK_KEY,lockValue);
          while(result == 0){
            //如果获取锁失败，则等待一段实践后重新获取
            try{
              Thread.sleep(100);
            } catch (InterruptedException e){
              e.printStackTrace();
            }
            result = jedis.setnx(LOCK_KEY,lockValue);
          }

          try{
            //再次检查库存数量是否大于0
            stock = jedis.get(STOCK_KEY);
            if(Integer.parseInt(stock) <= 0){
              System.out.println(userId + "秒杀失败，库存不足");
              return;
            }
            //减少库存数量，并将用户的请求从队列中删除
            jedis.decr(STOCK_KEY);
            jedis.lrem(ORDER_QUEUE_KEY,0,userId);
            System.out.println(userId + "秒杀成功，库存剩余不足:" + jedis.get(STOCK_KEY));
          } finally {
            //释放锁
            if(lockValue.equals(jedis.get(LOCK_KEY))){
              jedis.del(LOCK_KEY);
            }
          }

        }

      }
}
