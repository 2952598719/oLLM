  import { useState, useRef, useEffect } from 'react';
  import { cn } from '@/lib/utils';
  import { ChatHistory } from '@/components/chat/ChatHistory';
  import { ChatInput } from '@/components/chat/ChatInput';
  import { TypingIndicator } from '@/components/chat/TypingIndicator';
  import { AuthModal } from '@/components/auth/AuthModal';
  import KnowledgeBaseUploadModal from '@/components/knowledgeBase/KnowledgeBaseUploadModal';
  import { useContext } from 'react';
  import { AuthContext } from '@/contexts/authContext';
  import { toast } from 'sonner';
 

// API Response Types
interface TagResponseDTO {
  tagId: string;
  tagName: string;
}

interface ChatResponseDTO {
  chatId: string;
  title: string;
  updatedAt: string;
}

interface MessageResponseDTO {
  messageId: string;
  role: 'user' | 'assistant';
  content: string;
  createdAt: string;
}

// Application Types
export interface Message {
  id: string;
  content: string;
  role: 'user' | 'assistant';
  timestamp: Date;
}

export interface Conversation {
  id: string;
  title: string;
  createdAt: Date;
  messages: Message[];
}

// API Service Functions
const API_BASE_URL = 'http://localhost:8090/api/v1/openai';

const fetchChatList = async (): Promise<ChatResponseDTO[]> => {
  const response = await fetch(`${API_BASE_URL}/chat_list`, {
    credentials: 'include', // Include cookies for session management
  });
  
  if (!response.ok) {
    throw new Error('Failed to fetch chat list');
  }
  
  return response.json();
};

const createChat = async (prefixString: string): Promise<string> => {
  const response = await fetch(`${API_BASE_URL}/create_chat?prefixString=${encodeURIComponent(prefixString)}`, {
    method: 'POST',
    credentials: 'include',
  });
  
  if (!response.ok) {
    throw new Error('Failed to create chat');
  }
  
  // Parse as string to preserve precision
  const chatId = await response.text();
  return chatId;
}

const deleteChat = async (chatId: string): Promise<void> => {
  const response = await fetch(`${API_BASE_URL}/delete_chat?chatId=${chatId}`, {
    method: 'DELETE',
    credentials: 'include',
  });
  
  if (!response.ok) {
    throw new Error('Failed to delete chat');
  }
}

const fetchMessageList = async (chatId: string): Promise<MessageResponseDTO[]> => {
  const response = await fetch(`${API_BASE_URL}/message_list?chatId=${chatId}`, {
    credentials: 'include',
  });
  
  if (!response.ok) {
    throw new Error('Failed to fetch message list');
  }
  
  return response.json();
}

const generateStream = async (
  chatId: string, 
  model: string, 
  message: string,
  tagId?: string
): Promise<ReadableStream> => {
  const endpoint = tagId && tagId.trim() ? 'generate_stream_rag' : 'generate_stream';
  let url = `http://localhost:8090/api/v1/openai/${endpoint}?chatId=${chatId}&model=${encodeURIComponent(model)}&message=${encodeURIComponent(message)}`;
  
  // Add tagId parameter if selected
  if (tagId && tagId.trim()) {
    url += `&tagId=${encodeURIComponent(tagId)}`;
  }
  
  const response = await fetch(url, {
    method: 'GET',
    credentials: 'include',
    headers: {
      'Accept': 'text/event-stream', // 明确要求SSE格式
    },
  });
  
  if (!response.ok || !response.body) {
    const errorText = await response.text().catch(() => 'Unknown error');
    throw new Error(`Failed to generate stream: ${errorText}`);
  }
  
  return response.body;
}

// 移除外部定义的fetchTagList函数

export default function Home() {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [currentConversationId, setCurrentConversationId] = useState<string | null>(null);
  const [input, setInput] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [messages, setMessages] = useState<Message[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [authModalOpen, setAuthModalOpen] = useState(false);
  const [knowledgeBaseModalOpen, setKnowledgeBaseModalOpen] = useState(false);
  const { isAuthenticated, logout } = useContext(AuthContext);
  const [isLoadingChatList, setIsLoadingChatList] = useState(false);
  const [isLoadingMessages, setIsLoadingMessages] = useState(false);
  // Knowledge base state
  const [tagList, setTagList] = useState<TagResponseDTO[]>([]);
  const [selectedTagId, setSelectedTagId] = useState('');
  const [isLoadingTags, setIsLoadingTags] = useState(false);
  
  // Fetch knowledge base list
  const fetchTagList = async () => {
    if (!isAuthenticated) return;
    
    setIsLoadingTags(true);
    try {
      const response = await fetch('http://localhost:8090/api/v1/rag/query_tag_list', {
        credentials: 'include',
      });
      
      if (!response.ok) {
        throw new Error('Failed to fetch knowledge base list');
      }
      
      const data = await response.json();
      setTagList(data);
    } catch (error) {
      console.error('Error fetching knowledge base list:', error);
      toast.error('获取知识库列表失败');
    } finally {
      setIsLoadingTags(false);
    }
  };
  
// Create a new conversation - switches to "not in conversation" state
const createNewConversation = () => {
  // If in a conversation with messages, switch to no conversation state
  if (currentConversationId !== null) {
    setCurrentConversationId(null);
    setMessages([]);
    setInput('');
  }
};

// Handle conversation selection
const handleConversationSelect = async (conversationId: string) => {
  if (conversationId === currentConversationId) return;
  
  setIsLoadingMessages(true);
  try {
    setCurrentConversationId(conversationId);
    
    // Fetch message list for selected conversation
    const messageList = await fetchMessageList(conversationId);
    
    // Convert API response to our Message type
    const formattedMessages = messageList.map(msg => ({
      id: msg.messageId,
      content: msg.content,
      role: msg.role as 'user' | 'assistant',
      timestamp: new Date(msg.createdAt)
    }));
    
    setMessages(formattedMessages);
    
    // Update the conversation with messages
    setConversations(prev => 
      prev.map(conv => 
        conv.id === conversationId 
          ? { ...conv, messages: formattedMessages } 
          : conv
      )
    );
  } catch (error) {
    console.error('Error fetching message list:', error);
    toast.error('Failed to load conversation messages');
  } finally {
    setIsLoadingMessages(false);
  }
};
  
// Get current conversation
const currentConversation = conversations.find(
  conv => conv.id === currentConversationId
);

// Fetch chat list when authenticated
useEffect(() => {
  if (isAuthenticated) {
    const loadChatList = async () => {
      setIsLoadingChatList(true);
      try {
        const chatList = await fetchChatList();
        // Convert API response to our Conversation type
        const formattedConversations = chatList.map(chat => ({
          id: chat.chatId,
          title: chat.title,
          createdAt: new Date(chat.updatedAt),
          messages: []
        }));
        
        setConversations(formattedConversations);
        
        // If there are conversations, set the first one as active
        if (formattedConversations.length > 0) {
           setCurrentConversationId(formattedConversations[0].id.toString());
           // Load messages for the first conversation
           handleConversationSelect(formattedConversations[0].id.toString());
        } else {
  // If no conversations, set to no conversation state
  setCurrentConversationId(null);
  setMessages([]);
}
      } catch (error) {
        console.error('Error fetching chat list:', error);
        toast.error('Failed to load conversations');
      } finally {
        setIsLoadingChatList(false);
      }
    };
    
    loadChatList();
  } else {
    // If not authenticated, clear conversations
    setConversations([]);
    setCurrentConversationId(null);
    setMessages([]);
  }
  }, [isAuthenticated]);
  
  // Fetch knowledge base list when authenticated
  useEffect(() => {
    if (isAuthenticated) {
      fetchTagList();
    } else {
      setTagList([]);
      setSelectedTagId('');
    }
  }, [isAuthenticated]);

// Load messages when current conversation changes
useEffect(() => {
  if (currentConversationId && isAuthenticated && conversations.length > 0) {
    handleConversationSelect(currentConversationId);
  }
}, [currentConversationId, isAuthenticated]);
  
// Delete conversation
const deleteConversation = async (id: string) => {
  setIsStreaming(false);
  
  try {
    // Call API to delete the chat
    await deleteChat(id);
    
    // Update local state
    setConversations(prev => prev.filter(conv => conv.id !== id));
    
    // If deleting current conversation, switch to another or create new
    if (currentConversationId === id) {
      const remainingConversations = conversations.filter(conv => conv.id !== id);
      if (remainingConversations.length > 0) {
        handleConversationSelect(remainingConversations[0].id);
      } else {
        createNewConversation();
      }
    }
    
    toast.success('Conversation deleted successfully');
  } catch (error) {
    console.error('Error deleting conversation:', error);
    toast.error('Failed to delete conversation');
  }
};
  
  // Update conversation title based on first message
  const updateConversationTitle = (conversationId: string, firstMessage: string) => {
    setConversations(prev => 
      prev.map(conv => 
        conv.id === conversationId 
          ? { ...conv, title: firstMessage.length > 30 
              ? firstMessage.substring(0, 30) + '...' 
              : firstMessage 
            } 
          : conv
      )
    );
  };
  
  // Scroll to bottom when new messages arrive
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isStreaming]);
  
// Handle sending a message
const handleSendMessage = async () => {
  if (!input.trim() || isStreaming) return;
  
  // 未登录时显示登录弹窗
  if (!isAuthenticated) {
    setAuthModalOpen(true);
    return;
  }
  
  const userInput = input.trim();
  
  // Add user message to UI immediately (optimistic UI update)
  const userMessage: Message = {
    id: Date.now(), // Temporary ID
    content: userInput,
    role: 'user',
    timestamp: new Date()
  };
  
  // Update messages with user input
  setMessages(prev => [...prev, userMessage]);
  
  // Clear input immediately after sending
  setInput('');
  setIsStreaming(true);
  
  try {
     // Step 1: Create chat if not in a conversation
     let chatId: string;
     if (currentConversationId === null) {
       // Create new chat - use first 10 characters of user input as prefix
       const prefixString = userInput.substring(0, 10);
       chatId = await createChat(prefixString);
      
      // Add the new conversation to the list
      const newConversation: Conversation = {
        id: chatId,
        title: prefixString || 'New Conversation',
        createdAt: new Date(),
        messages: [userMessage]
      };
      
      setConversations(prev => [newConversation, ...prev]);
      setCurrentConversationId(chatId);
    } else {
      chatId = currentConversationId;
    }
    
    // Step 2: Update current conversation with user message
    if (chatId) {
      setConversations(prev => 
        prev.map(conv => 
          conv.id === chatId 
            ? { ...conv, messages: [...conv.messages, userMessage] } 
            : conv
        )
      );
    }
    
    // Step 3: Generate stream with the message
      // 传入选中的tagId，如果有的话
      const stream = await generateStream(chatId, 'deepseek-chat', userInput, selectedTagId);
    
     // Create a reader for the stream
     const reader = stream.getReader();
     const decoder = new TextDecoder();
     let buffer = ''; // 用于累积流数据
     
     // Create initial assistant message
     let assistantMessageId = Date.now() + 1; // Temporary ID
     const initialAssistantMessage: Message = {
       id: assistantMessageId,
       content: '',
       role: 'assistant',
       timestamp: new Date()
     };
     
     // Add initial empty message to messages state
     setMessages(prev => [...prev, initialAssistantMessage]);
     
     // Update conversation with initial assistant message
     if (chatId) {
       setConversations(prev => 
         prev.map(conv => 
           conv.id === chatId 
             ? { ...conv, messages: [...conv.messages, initialAssistantMessage] } 
             : conv
         )
       );
     }
     
     // Process the stream - SSE format
     while (true) {
       const { done, value } = await reader.read();
       
       if (done) {
         // 检查是否有未处理的缓冲区数据
         if (buffer.trim()) {
           handleSSEData(buffer.trim());
         }
         break;
       }
       
       // 解码新块并添加到缓冲区
       buffer += decoder.decode(value, { stream: true });
       
       // 分割缓冲区中的完整SSE事件 (SSE事件以"\n\n"分隔)
       const events = buffer.split('\n\n');
       
       // 保留最后一个不完整的事件（如果有）
       buffer = events.pop() || '';
       
       // 处理每个完整的SSE事件
       for (const event of events) {
         if (event.trim()) {
           handleSSEData(event.trim());
         }
       }
     }
     
     // 处理SSE数据字段的辅助函数
     function handleSSEData(data: string) {
       // SSE数据通常以"data:"开头
       const dataPrefix = 'data:';
       if (data.startsWith(dataPrefix)) {
         const content = data.substring(dataPrefix.length).trim();
         
         if (content) {
           // 尝试解析JSON（如果后端返回JSON格式）
          try {
            const parsedContent = JSON.parse(content);
            // 提取嵌套的content字段
            const messageContent = parsedContent.result?.output?.content || '';
            // 检查是否结束
            const finishReason = parsedContent.result?.output?.properties?.finishReason;
            
            if (messageContent) {
              updateAssistantMessage(messageContent);
            }
            
            if (finishReason === 'STOP') {
              // 可以在这里添加流结束的处理逻辑
              console.log('Stream completed');
            }
          } catch (e) {
            console.error('Error parsing stream content:', e);
            // 出错时不更新消息，避免显示错误内容
           }
         }
       } else if (data.startsWith('error:')) {
         // 处理错误消息
         const errorMessage = data.substring('error:'.length).trim();
         toast.error(`Error: ${errorMessage}`);
       }
     }
     
     // 更新助手消息的辅助函数
     function updateAssistantMessage(content: string) {
       // Update the assistant message with new content
       setMessages(prev => 
         prev.map(msg => 
           msg.id === assistantMessageId 
             ? { ...msg, content: msg.content + content } 
             : msg
         )
       );
       
       // Update conversation with new content
       if (chatId) {
         setConversations(prev => 
           prev.map(conv => 
             conv.id === chatId 
               ? { 
                   ...conv, 
                   messages: conv.messages.map(msg => 
                     msg.id === assistantMessageId 
                       ? { ...msg, content: msg.content + content }
                       : msg
                   ) 
                 } 
               : conv
           )
         );
       }
     }
    
    reader.releaseLock();
    
  } catch (error) {
    console.error('Error sending message:', error);
    toast.error('Failed to send message');
    // Remove the user message from state since sending failed
    setMessages(prev => prev.filter(msg => msg.id !== userMessage.id));
  } finally {
    setIsStreaming(false);
  }
};
  
  // Handle Enter key press
  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };
  
  return (
    <div className="flex h-screen bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-900 dark:to-slate-800 overflow-hidden">
      {/* Sidebar - Conversation List */}
      <div className={`bg-white dark:bg-slate-900 border-r border-slate-200 dark:border-slate-700 transition-all duration-300 ease-in-out ${
        sidebarOpen ? 'w-64' : 'w-0 md:w-20'
      } flex flex-col h-full shadow-sm z-10`}>
         
         {/* Sidebar Toggle Button */}
         <button
           onClick={() => setSidebarOpen(!sidebarOpen)}
            className="absolute left-4 bottom-4 -translate-x-1/2 w-6 h-6 rounded-full bg-white dark:bg-slate-800 border border-slate-300 dark:border-slate-600 flex items-center justify-center shadow-md z-20 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
           aria-label={sidebarOpen ? "Collapse sidebar" : "Expand sidebar"}
         >
           <i className={`fa-solid ${sidebarOpen ? 'fa-angle-left' : 'fa-angle-right'} text-slate-700 dark:text-slate-300`}></i>
         </button>
         
         {/* New Conversation Button */}
         <div className={`p-4 ${!sidebarOpen && 'md:p-4'}`}>
           <button
              onClick={() => {
                createNewConversation();
              }}
             className={`w-full py-2 px-4 rounded-lg bg-gradient-to-r from-blue-600 to-indigo-600 text-white font-medium transition-all duration-200 hover:opacity-90 flex items-center justify-center gap-2 ${
               !sidebarOpen && 'md:justify-center md:px-2 rounded-full'
             }`}
           >
             <i className="fa-solid fa-plus"></i>
             {sidebarOpen && <span>New Chat</span>}
           </button>
         </div>
        
         {/* Conversation List */}
         <div className="flex-1 overflow-y-auto p-2">
           {isLoadingChatList ? (
             <div className="space-y-2 p-2">
               {[1, 2, 3].map((i) => (
                 <div key={i} className="p-3 rounded-lg bg-slate-100 dark:bg-slate-800 animate-pulse">
                   <div className="h-4 bg-slate-200 dark:bg-slate-700 rounded w-3/4 mb-2"></div>
                   <div className="h-3 bg-slate-200 dark:bg-slate-700 rounded w-1/2"></div>
                 </div>
               ))}
             </div>
           ) : sidebarOpen ? (
             <div className="space-y-1">
               {conversations.map((conversation) => (
                 <div 
                   key={conversation.id}
                   onClick={() => handleConversationSelect(conversation.id)}
                   className={`p-3 rounded-lg cursor-pointer transition-all duration-200 flex items-center justify-between ${
                     currentConversationId === conversation.id
                       ? 'bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800'
                       : 'hover:bg-slate-100 dark:hover:bg-slate-800'
                   }`}
                 >
                   <div className="flex-1 min-w-0">
                     <h3 className="font-medium text-sm truncate">{conversation.title}</h3>
                     <p className="text-xs text-slate-500 dark:text-slate-400 truncate">
                       {new Date(conversation.createdAt).toLocaleTimeString()}
                     </p>
                   </div>
                    <button 
                      onClick={(e) => {
                        e.stopPropagation();
                        deleteConversation(conversation.id);
                      }}
                       className="ml-2 text-gray-500 hover:text-red-500 p-1 rounded-full hover:bg-slate-100 dark:hover:bg-slate-800"
                     
                     >
                      <i className="fa-solid fa-trash"></i>
                   </button>
                 </div>
               ))}
             </div>
           ) : (
             <div className="flex flex-col items-center space-y-3">
               {conversations.map((conversation) => (
                 <div 
                   key={conversation.id}
                   onClick={() => handleConversationSelect(conversation.id)}
                   className={`w-10 h-10 rounded-full flex items-center justify-center cursor-pointer transition-all duration-200 ${
                     currentConversationId === conversation.id
                       ? 'bg-blue-50 dark:bg-blue-900/20 border-2 border-blue-500 dark:border-blue-400'
                       : 'bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 dark:hover:bg-slate-700'
                   }`}
                 >
                   <i className="fa-solid fa-comment"></i>
                 </div>
               ))}
             </div>
           )}
         </div>
      </div>
      
      {/* Main Content Area */}
      <div className={`flex-1 flex flex-col overflow-hidden transition-all duration-300 ${
        sidebarOpen ? 'ml-0' : '-ml-64 md:ml-0'
      }`}>
        {/* Header */}
         <header className="border-b border-slate-200 dark:border-slate-700 bg-white/80 dark:bg-slate-900/80 backdrop-blur-sm py-4 px-6 shadow-sm flex justify-between items-center">
           <div>
             <h1 className="text-2xl font-bold bg-gradient-to-r from-blue-600 to-indigo-600 dark:from-blue-400 dark:to-indigo-400 bg-clip-text text-transparent">
               AI Chat Assistant
             </h1>
             <p className="text-sm text-slate-500 dark:text-slate-400">
               Have a conversation with our AI assistant
             </p>
           </div>
           
            <div className="flex items-center gap-4">
                {/* 知识库选择下拉框和上传按钮 - 仅登录用户可见 */}
                {isAuthenticated && (
                  <div className="flex items-center gap-4">
                    {/* 知识库选择下拉框 */}
                    <div className="relative">
                      <select
                        value={selectedTagId}
                        onChange={(e) => setSelectedTagId(e.target.value)}
                        disabled={isLoadingTags}
                       className="py-2 px-4 pr-8 rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500 appearance-none w-64"
                      >
                        <option value="">...</option>
                        {isLoadingTags ? (
                          <option value="" disabled>加载中...</option>
                        ) : (
                          tagList.map(tag => (
                            <option key={tag.tagId} value={tag.tagId}>
                              {tag.tagName}
                            </option>
                          ))
                        )}
                      </select>
                      <div className="absolute right-3 top-1/2 transform -translate-y-1/2 pointer-events-none">
                        <i className="fa-solid fa-chevron-down text-slate-500 dark:text-slate-400 text-xs"></i>
                      </div>
                    </div>
                    
                    {/* 上传知识库按钮 */}
                    <button
                      onClick={() => setKnowledgeBaseModalOpen(true)}
                      className="py-2 px-4 rounded-lg bg-gradient-to-r from-green-600 to-teal-600 text-white font-medium transition-all duration-200 hover:opacity-90"
                    >
                      <i className="fa-solid fa-upload mr-2"></i>上传知识库
                    </button>
                  </div>
                )}
              
              {isAuthenticated ? (
                <button
                  onClick={logout}
                  className="py-2 px-4 rounded-lg bg-slate-200 dark:bg-slate-700 text-slate-800 dark:text-slate-200 font-medium transition-all duration-200 hover:bg-slate-300 dark:hover:bg-slate-600"
                >
                  <i className="fa-solid fa-sign-out mr-2"></i>退出登录
                </button>
              ) : (
                <button
                  onClick={() => setAuthModalOpen(true)}
                  className="py-2 px-4 rounded-lg bg-gradient-to-r from-blue-600 to-indigo-600 text-white font-medium transition-all duration-200 hover:opacity-90"
                >
                  <i className="fa-solid fa-user-circle mr-2"></i>登录/注册
                </button>
              )}
            </div>
         </header>
        
        {/* Main chat area */}
        <main className="flex-1 overflow-y-auto p-4">
          <div className="max-w-3xl mx-auto space-y-6">
             {/* Loading messages indicator */}
              {isLoadingMessages ? (
                <div className="flex justify-center py-12">
                  <div className="inline-flex flex-col items-center">
                    <div className="w-12 h-12 rounded-full border-4 border-blue-200 border-t-blue-600 animate-spin mb-4"></div>
                    <p className="text-slate-500 dark:text-slate-400">Loading conversation...</p>
                  </div>
                </div>
              ) : currentConversationId === null ? (
                <div className="text-center py-12">
                  <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 mb-4">
                    <i className="fa-solid fa-plus-circle text-2xl"></i>
                  </div>
                  <h2 className="text-xl font-semibold text-slate-800 dark:text-slate-200 mb-2">
                    No Active Conversation
                  </h2>
                  <p className="text-slate-500 dark:text-slate-400 max-w-md mx-auto">
                    Type your message below to start a new conversation.
                  </p>
                </div>
              ) : messages.length === 0 && !isStreaming ? (
                <div className="text-center py-12">
                  <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 mb-4">
                    <i className="fa-solid fa-comments text-2xl"></i>
                  </div>
                  <h2 className="text-xl font-semibold text-slate-800 dark:text-slate-200 mb-2">
                    New Conversation
                  </h2>
                  <p className="text-slate-500 dark:text-slate-400 max-w-md mx-auto">
                    Start typing your message below to chat with our AI assistant.
                  </p>
                </div>
              ) : null}
            
            {/* Chat history */}
            <ChatHistory messages={messages} />
             
            
            {/* Scroll anchor */}
            <div ref={messagesEndRef} />
          </div>
        </main>
        
        {/* Input area */}
        <footer className="border-t border-slate-200 dark:border-slate-700 bg-white/80 dark:bg-slate-900/80 backdrop-blur-sm p-4">
          <div className="max-w-3xl mx-auto">
            <ChatInput 
              input={input}
              setInput={setInput}
              onSend={handleSendMessage}
              onKeyPress={handleKeyPress}
              disabled={isStreaming}
            />
          </div>
        </footer>
       </div>
       
        {/* Authentication Modal */}
        <AuthModal 
          isOpen={authModalOpen} 
          onClose={() => setAuthModalOpen(false)} 
        />
        
        {/* Knowledge Base Upload Modal */}
        <KnowledgeBaseUploadModal
          isOpen={knowledgeBaseModalOpen}
          onClose={() => setKnowledgeBaseModalOpen(false)}
        />
     </div>
   );
}