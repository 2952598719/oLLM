import { Message } from '@/pages/Home';
import { ChatMessage } from './ChatMessage';

interface ChatHistoryProps {
  messages: Message[];
}

export function ChatHistory({ messages }: ChatHistoryProps) {
  return (
    <div className="space-y-4">
      {messages.map((message) => (
        <ChatMessage 
          key={message.id} 
          content={message.content} 
          role={message.role} 
          timestamp={message.timestamp} 
          isLoading={message.isLoading}
        />
      ))}
    </div>
  );
}