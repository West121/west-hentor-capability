import { useEffect, useMemo, useState } from 'react';
import {
  App as AntdApp,
  Avatar,
  Badge,
  Button,
  Col,
  Empty,
  Input,
  Popconfirm,
  Row,
  Space,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { CheckOutlined, SendOutlined, StopOutlined, SyncOutlined, UserAddOutlined, UserOutlined } from '@ant-design/icons';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import type { ChatMessageItem, FriendItem } from '../../types/domain';

// Chat page mirrors ChatAppService and FriendshipAppService behavior.
export default function ChatPage() {
  const { message } = AntdApp.useApp();
  const [friends, setFriends] = useState<FriendItem[]>([]);
  const [messages, setMessages] = useState<ChatMessageItem[]>([]);
  const [selectedFriendId, setSelectedFriendId] = useState<number>();
  const [draft, setDraft] = useState('');
  const [newFriendName, setNewFriendName] = useState('');
  const [loading, setLoading] = useState(false);

  const selectedFriend = useMemo(
    () => friends.find((item) => item.friendUserId === selectedFriendId) ?? friends[0],
    [friends, selectedFriendId],
  );

  async function load(nextFriendId = selectedFriendId) {
    setLoading(true);
    try {
      const data = await api.chatFriends();
      setFriends(data.friends);
      const next = data.friends.find((item) => item.friendUserId === nextFriendId) ?? data.friends[0];
      setSelectedFriendId(next?.friendUserId);
      await loadMessages(next);
    } finally {
      setLoading(false);
    }
  }

  async function loadMessages(friend?: FriendItem) {
    if (!friend) {
      setMessages([]);
      return;
    }
    const data = await api.chatMessages(friend.friendUserId, friend.friendTenantId);
    setMessages(data.items);
    if ((friend.unreadMessageCount ?? 0) > 0) {
      await api.markChatRead(friend.friendUserId, friend.friendTenantId);
      setFriends((items) =>
        items.map((item) => (item.friendUserId === friend.friendUserId ? { ...item, unreadMessageCount: 0 } : item)),
      );
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function selectFriend(friend: FriendItem) {
    setSelectedFriendId(friend.friendUserId);
    await loadMessages(friend);
  }

  async function sendMessage() {
    if (!selectedFriend || !draft.trim()) {
      return;
    }
    await api.sendChatMessage(selectedFriend.friendUserId, selectedFriend.friendTenantId, draft.trim());
    setDraft('');
    await loadMessages(selectedFriend);
  }

  async function addFriend() {
    if (!newFriendName.trim()) {
      return;
    }
    const friend = await api.createFriendshipRequestByUserName(newFriendName.trim());
    setNewFriendName('');
    message.success('好友已添加');
    await load(friend.friendUserId);
  }

  async function toggleBlock(friend: FriendItem) {
    if (friend.state === 2) {
      await api.unblockFriend(friend.friendUserId, friend.friendTenantId);
      message.success('已解除拉黑');
    } else {
      await api.blockFriend(friend.friendUserId, friend.friendTenantId);
      message.warning('已拉黑好友');
    }
    await load(friend.friendUserId);
  }

  async function accept(friend: FriendItem) {
    await api.acceptFriendshipRequest(friend.friendUserId, friend.friendTenantId);
    message.success('好友请求已接受');
    await load(friend.friendUserId);
  }

  return (
    <div className="page-body">
      <PageTitle title="聊天" description="维护好友关系、查看会话消息并发送本地聊天消息" />
      <Row gutter={16} style={{ minHeight: 620 }}>
        <Col xs={24} lg={8} xl={7}>
          <div className="ant-card ant-card-bordered" style={{ height: '100%' }}>
            <div className="ant-card-body" style={{ padding: 16 }}>
              <Space.Compact style={{ width: '100%', marginBottom: 12 }}>
                <Input
                  placeholder="输入用户名添加好友"
                  value={newFriendName}
                  onChange={(event) => setNewFriendName(event.target.value)}
                  onPressEnter={() => void addFriend()}
                />
                <Tooltip title="添加好友">
                  <Button icon={<UserAddOutlined />} onClick={() => void addFriend()} />
                </Tooltip>
                <Tooltip title="刷新">
                  <Button icon={<SyncOutlined />} loading={loading} onClick={() => void load()} />
                </Tooltip>
              </Space.Compact>

              {friends.length ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {friends.map((friend) => (
                    <button
                      key={`${friend.friendTenantId ?? 'host'}-${friend.friendUserId}`}
                      type="button"
                      onClick={() => void selectFriend(friend)}
                      style={{
                        width: '100%',
                        cursor: 'pointer',
                        border: 0,
                        borderRadius: 6,
                        padding: '8px 10px',
                        textAlign: 'left',
                        background: friend.friendUserId === selectedFriend?.friendUserId ? '#f0f7ff' : '#fff',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                        <Badge count={friend.unreadMessageCount} size="small">
                          <Avatar icon={<UserOutlined />} />
                        </Badge>
                        <div style={{ minWidth: 0, flex: 1 }}>
                          <Typography.Text strong style={{ display: 'block' }}>
                            {friend.friendUserName}
                          </Typography.Text>
                          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            {friend.friendTenancyName || 'Host'}
                          </Typography.Text>
                        </div>
                        {friend.state === 2 ? <Tag color="red">已拉黑</Tag> : friend.isOnline ? <Tag color="green">在线</Tag> : <Tag>离线</Tag>}
                      </div>
                    </button>
                  ))}
                </div>
              ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无好友" />
              )}
            </div>
          </div>
        </Col>

        <Col xs={24} lg={16} xl={17}>
          <div className="ant-card ant-card-bordered" style={{ minHeight: 620 }}>
            <div className="ant-card-body" style={{ padding: 0 }}>
              {selectedFriend ? (
                <>
                  <div
                    style={{
                      height: 56,
                      paddingInline: 16,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      borderBottom: '1px solid #f0f0f0',
                    }}
                  >
                    <Space>
                      <Avatar icon={<UserOutlined />} />
                      <Typography.Text strong>{selectedFriend.friendUserName}</Typography.Text>
                      {selectedFriend.state === 2 ? <Tag color="red">已拉黑</Tag> : null}
                    </Space>
                    <Space>
                      <Button icon={<CheckOutlined />} onClick={() => void accept(selectedFriend)}>
                        接受
                      </Button>
                      <Popconfirm
                        title={selectedFriend.state === 2 ? '确定解除拉黑吗？' : '确定拉黑该好友吗？'}
                        onConfirm={() => void toggleBlock(selectedFriend)}
                      >
                        <Button danger={selectedFriend.state !== 2} icon={<StopOutlined />}>
                          {selectedFriend.state === 2 ? '解除拉黑' : '拉黑'}
                        </Button>
                      </Popconfirm>
                    </Space>
                  </div>

                  <div style={{ height: 464, overflowY: 'auto', padding: 16, background: '#fafafa' }}>
                    {messages.length ? (
                      messages.map((item) => (
                        <div
                          key={item.id}
                          style={{
                            display: 'flex',
                            justifyContent: item.side === 1 ? 'flex-end' : 'flex-start',
                            marginBottom: 12,
                          }}
                        >
                          <div
                            style={{
                              maxWidth: '72%',
                              padding: '8px 12px',
                              borderRadius: 8,
                              background: item.side === 1 ? '#1677ff' : '#fff',
                              color: item.side === 1 ? '#fff' : undefined,
                              border: item.side === 1 ? undefined : '1px solid #f0f0f0',
                            }}
                          >
                            <div>{item.message}</div>
                            <Typography.Text style={{ color: item.side === 1 ? 'rgba(255,255,255,0.78)' : undefined, fontSize: 12 }}>
                              {item.creationTime}
                            </Typography.Text>
                          </div>
                        </div>
                      ))
                    ) : (
                      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无消息" />
                    )}
                  </div>

                  <div style={{ padding: 16, borderTop: '1px solid #f0f0f0' }}>
                    <Space.Compact style={{ width: '100%' }}>
                      <Input.TextArea
                        autoSize={{ minRows: 1, maxRows: 4 }}
                        placeholder="输入消息"
                        value={draft}
                        disabled={selectedFriend.state === 2}
                        onChange={(event) => setDraft(event.target.value)}
                        onPressEnter={(event) => {
                          if (!event.shiftKey) {
                            event.preventDefault();
                            void sendMessage();
                          }
                        }}
                      />
                      <Button type="primary" icon={<SendOutlined />} disabled={selectedFriend.state === 2} onClick={() => void sendMessage()}>
                        发送
                      </Button>
                    </Space.Compact>
                  </div>
                </>
              ) : (
                <div style={{ minHeight: 620, display: 'grid', placeItems: 'center' }}>
                  <Empty description="请选择好友" />
                </div>
              )}
            </div>
          </div>
        </Col>
      </Row>
    </div>
  );
}
