package com.leetmodel.common.api.messaging;

/**
 * 业务文件绑定与解绑事件的稳定契约。
 *
 * <p>由业务服务作为引用事实所有者生产，file-service 幂等消费并维护引用投影。</p>
 */
public final class FileBindingMessageContract {

    /** 文件引用事件逻辑 Topic。 */
    public static final String TOPIC = "file-event-v1";

    /** 建立绑定事件类型。 */
    public static final String BOUND_EVENT_TYPE = "FILE_BOUND";

    /** 解除绑定事件类型。 */
    public static final String UNBOUND_EVENT_TYPE = "FILE_UNBOUND";

    /** file-service 引用投影消费组。 */
    public static final String CONSUMER_GROUP = "cg-file-binding-v1";

    private FileBindingMessageContract() {
    }

    /**
     * 生成绑定或解绑事件的业务幂等键。
     *
     * @param eventType 事件类型
     * @param fileId 文件资产标识
     * @param resourceId 业务资源标识
     * @param eventVersion 事件版本
     * @return 长度受控的幂等键
     */
    public static String idempotencyKey(String eventType, Long fileId, String resourceId, long eventVersion) {
        String prefix = BOUND_EVENT_TYPE.equals(eventType) ? "bound" : "unbound";
        return prefix + ":" + fileId + ":" + resourceId + ":" + eventVersion;
    }
}
