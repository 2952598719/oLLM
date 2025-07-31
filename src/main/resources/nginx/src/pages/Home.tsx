import { useState, useRef, useEffect } from 'react';
import { cn } from '@/lib/utils';
import { ChatHistory } from '@/components/chat/ChatHistory';
import { ChatInput } from '@/components/chat/ChatInput';
import { TypingIndicator } from '@/components/chat/TypingIndicator';

// Define conversation and message types
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

export default function Home() {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [currentConversationId, setCurrentConversationId] = useState<string | null>(null);
  const [input, setInput] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [messages, setMessages] = useState<Message[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  
  // Create a new conversation
  const createNewConversation = () => {
    const newConversation: Conversation = {
      id: `conv-${Date.now()}`,
      title: 'New Conversation',
      createdAt: new Date(),
      messages: []
    };
    
    setConversations(prev => [newConversation, ...prev]);
    setCurrentConversationId(newConversation.id);
    setInput('');
  };
  
// Get current conversation
const currentConversation = conversations.find(
  conv => conv.id === currentConversationId
);

// Initialize with a conversation if none exists
useEffect(() => {
  if (conversations.length === 0) {
    createNewConversation();
  } else if (currentConversation) {
    setMessages(currentConversation.messages);
  }
}, [currentConversation, conversations]);
  
  // Delete conversation
  const deleteConversation = (id: string) => {
    setConversations(prev => prev.filter(conv => conv.id !== id));
    
    // If deleting current conversation, switch to another or create new
    if (currentConversationId === id) {
      if (conversations.length > 1) {
        setCurrentConversationId(conversations.find(conv => conv.id !== id)?.id || null);
      } else {
        createNewConversation();
      }
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
  
  // Mock streaming response simulation
  const simulateStreamingResponse = (userMessage: string) => {
    setIsStreaming(true);
    
    // Generate mock responses based on user input
    const responses = {
      'hello': 'Hello! How can I assist you today? I\'m here to help with any questions or tasks you might have.',
      'hi': 'Hi there! What can I do for you today? Feel free to ask me anything.',
      'how are you': 'I\'m just a computer program, but thanks for asking! I\'m here and ready to help you with whatever you need.',
      'what can you do': 'I can help answer questions, provide information, assist with writing, explain concepts, and much more! Just let me know what you need.',
      'default': 'Thank you for your message! I\'m processing your request. This is a simulated streaming response to demonstrate how the conversation would flow in real-time with an AI model.'
    };
    
    // Select appropriate response or use default
    const responseText = responses[userMessage.toLowerCase() as keyof typeof responses] || responses.default;
     
      // Stream the response character by character
    let index = 0;
    let newMessageId = `msg-${Date.now()}-assistant`;
    
    // Create empty initial message
    const initialAssistantMessage: Message = {
      id: newMessageId,
      content: '',
      role: 'assistant',
      timestamp: new Date()
    };
    
    // Add initial empty message to messages state
    setMessages(prev => [...prev, initialAssistantMessage]);
    
    // Update conversation with initial assistant message
    if (currentConversationId) {
      setConversations(prev => 
        prev.map(conv => 
          conv.id === currentConversationId 
            ? { ...conv, messages: [...conv.messages, initialAssistantMessage] } 
            : conv
        )
      );
    }
    
    const interval = setInterval(() => {
      if (index < responseText.length) {
        // Get current character (ensure it's a string)
        const currentChar = responseText.charAt(index) || '';
        
        // Update message with current character in messages state
        setMessages(prev => prev.map(msg => 
          msg.id === newMessageId 
            ? { ...msg, content: msg.content + currentChar }
            : msg
        ));
        
        // Update message in conversation state
        if (currentConversationId) {
          setConversations(prev => 
            prev.map(conv => 
              conv.id === currentConversationId 
                ? { 
                    ...conv, 
                    messages: conv.messages.map(msg => 
                      msg.id === newMessageId 
                        ? { ...msg, content: msg.content + currentChar }
                        : msg
                    ) 
                  } 
                : conv
            )
          );
        }
        
        index++;
      } else {
        clearInterval(interval);
        setIsStreaming(false);
      }
    }, 20); // Adjust speed here (lower = faster)
    
    return () => clearInterval(interval); // Cleanup function
  };
  
  // Handle sending a message
  const handleSendMessage = () => {
    if (!input.trim() || isStreaming) return;
    
    const userInput = input.trim();
    
    // Add user message to history
    const userMessage: Message = {
      id: `msg-${Date.now()}-user`,
      content: userInput,
      role: 'user',
      timestamp: new Date()
    };
    
    // Update messages with user input
    setMessages(prev => [...prev, userMessage]);
    
    // Update current conversation with the new message
    if (currentConversationId) {
      setConversations(prev => 
        prev.map(conv => 
          conv.id === currentConversationId 
            ? { ...conv, messages: [...conv.messages, userMessage] } 
            : conv
        )
      );
    }
    
    // Clear input immediately after sending
    setInput('');
    
    // Simulate AI response with the user input
    const cleanup = simulateStreamingResponse(userInput);
    
    // Cleanup on unmount
    return () => cleanup();
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

        
        {/* New Conversation Button */}
        <div className={`p-4 ${!sidebarOpen && 'md:p-4'}`}>
          <button
            onClick={createNewConversation}
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
          {sidebarOpen ? (
            <div className="space-y-1">
              {conversations.map((conversation) => (
                <div 
                  key={conversation.id}
                  onClick={() => setCurrentConversationId(conversation.id)}
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
                  onClick={() => setCurrentConversationId(conversation.id)}
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
        <header className="border-b border-slate-200 dark:border-slate-700 bg-white/80 dark:bg-slate-900/80 backdrop-blur-sm py-4 px-6 shadow-sm">
          <h1 className="text-2xl font-bold bg-gradient-to-r from-blue-600 to-indigo-600 dark:from-blue-400 dark:to-indigo-400 bg-clip-text text-transparent">
            AI Chat Assistant
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Have a conversation with our AI assistant
          </p>
        </header>
        
        {/* Main chat area */}
        <main className="flex-1 overflow-y-auto p-4">
          <div className="max-w-3xl mx-auto space-y-6">
            {/* Welcome message for empty chat */}
            {messages.length === 0 && !isStreaming && (
              <div className="text-center py-12">
                <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 mb-4">
                  <i className="fa-solid fa-comments text-2xl"></i>
                </div>
                <h2 className="text-xl font-semibold text-slate-800 dark:text-slate-200 mb-2">
                  Welcome to AI Chat
                </h2>
                <p className="text-slate-500 dark:text-slate-400 max-w-md mx-auto">
                  Start typing your message below to chat with our AI assistant.
                </p>
              </div>
            )}
            
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
    </div>
  );
}