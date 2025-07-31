import { motion } from 'framer-motion';
import { cn } from '@/lib/utils';

interface ChatInputProps {
  input: string;
  setInput: (value: string) => void;
  onSend: () => void;
  onKeyPress: (e: React.KeyboardEvent) => void;
  disabled: boolean;
}

export function ChatInput({ input, setInput, onSend, onKeyPress, disabled }: ChatInputProps) {
   // Only update input when user types, not from external sources
   const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
     if (!disabled) {
       setInput(e.target.value);
     }
   };
   
   return (
      <div className="flex items-center gap-2">
       <textarea
         value={input}
         onChange={handleChange}
         onKeyDown={onKeyPress}
         placeholder="Type your message... (Press Enter to send)"
         className={cn(
           "flex-1 min-h-[50px] max-h-[150px] p-3 rounded-2xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 resize-none focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 transition-all duration-200 text-slate-900 dark:text-slate-100",
           disabled ? "opacity-70 cursor-not-allowed" : ""
         )}
         disabled={disabled}
       />
       
       <motion.button
         onClick={onSend}
         disabled={!input.trim() || disabled}
         whileHover={{ scale: 1.05 }}
         whileTap={{ scale: 0.95 }}
         className={cn(
           "w-12 h-12 rounded-full flex items-center justify-center shadow-md transition-all duration-200",
           (!input.trim() || disabled)
             ? "bg-slate-200 dark:bg-slate-700 text-slate-400 cursor-not-allowed"
             : "bg-gradient-to-br from-blue-600 to-indigo-600 text-white hover:from-blue-500 hover:to-indigo-500"
         )}
       >
         <motion.i 
           className="fa-solid fa-paper-plane"
           animate={disabled ? undefined : { rotate: [0, 15, 0] }}
           transition={{ duration: 0.3 }}
         ></motion.i>
       </motion.button>
     </div>
   );
 }