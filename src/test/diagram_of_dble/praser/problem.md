##1. query(String sql,boolean isPrepared)当中的isPrepared是干什么用的，待会儿需要看
画图自己定一个标准，超出本系统的类不再进行跟踪。仅仅作为以后深入理解的依据。
##1.校验sql字符串格式
###1.1.如果sql字符串为空，报出错误码返回，（就是mysql协议校验的问题，简单）
###1.2.remove sql字符串后面的“;”.
###1.3.setExecuteSql(sql,isPrepared)清除预编译sql，如果不是预编译代码
###1.4.判断是否为Debug状态
###1.5.对sql语句进行防火墙sql策略进行校验
###1.6.执行setReadOnly(UserReadOnly);setSessionReadOnly(sessionReadOnly)等方法
###1.7.执行queryHandler.query(sql)
##2.serverqueryHandler.query(sql)
###2.1 接收连接端连接信息 连接号，IP，prot,user,pwd,事务隔离数量等
###2.2校验session.remingsql是否为空
###2.3 preliminary judgment of multi statement
###2.4 ServerParse,分析SQL语句判断sql类型
###2.5 singleNodeHandler.execute();
##3.singleNodeHandler.execute();
###3.1 记录本方法指向开始时间
###3.2 获取serverSession 类
###3.3 获取会话包id
###3.4 获取host node 节点信息
###3.5 设置结果集合
###3.6创建新连接
###3.7获取本实例的配置信息
###3.8获取物理机器DBnode
###3.9物理机节点开始连接
##4.PhysicalDBNode.getConnection();
###4.1判断是否为写操作
###4.2如果数据库从机连接不为空:如果是读提交，从数据库连接池里面获取读连接
##5 PhysicalDBpool.getRWBalanceCon
###5.1 获取ShareSource 信息
