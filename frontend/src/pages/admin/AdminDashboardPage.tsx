import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { adminApi } from '../../api';
import { DashboardStats } from '../../types';
import { useSettings } from '../../contexts/SettingsContext';
import { useLanguage } from '../../i18n';
import { Upload, Image as ImageIcon, Loader2, BarChart3, Bot } from 'lucide-react';
import AgentMonitorTab from '../../components/admin/AgentMonitorTab';

export default function AdminDashboardPage() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'overview' | 'agent-monitor'>('overview');
  const { t } = useLanguage();
  
  const { settings, updateSettingsPayload, loading: settingsLoading } = useSettings();
  const [formData, setFormData] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    adminApi.dashboard()
      .then(res => setStats(res.data.data as any))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!settingsLoading && Object.keys(formData).length === 0) {
      setFormData(settings);
    }
  }, [settings, settingsLoading, formData]);

  const handleSaveSettings = async () => {
    setSaving(true);
    try {
      await updateSettingsPayload(formData);
      alert('Visual settings updated successfully!');
    } catch (e) {
      alert('Failed to update settings');
    } finally {
      setSaving(false);
    }
  };

  const handleSettingChange = (key: string, value: string) => {
    setFormData(prev => ({ ...prev, [key]: value }));
  };

  const handleFileUpload = async (key: string, e: React.ChangeEvent<HTMLInputElement>) => {
    if (!e.target.files?.[0]) return;
    
    const file = e.target.files[0];
    const formDataUpload = new FormData();
    formDataUpload.append('file', file);
    
    setSaving(true);
    try {
      const res = await adminApi.uploadImage(formDataUpload);
      handleSettingChange(key, res.data.data);
    } catch (err) {
      alert('Upload failed: ' + err);
    } finally {
      setSaving(false);
      e.target.value = '';
    }
  };

  const isImageField = (key: string) => {
    return key.startsWith('hero_');
  };

  const getFriendlyName = (key: string) => {
    return t(`admin.img_${key.replace('hero_', '')}`);
  };

  const CATEGORY_HERO_KEYS = [
    'hero_LIVE_MUSIC',
    'hero_ARTS',
    'hero_SPORTS',
    'hero_WORKSHOPS',
    'hero_EXPERIENCES',
    'hero_OTHERS',
    'hero_fallback'
  ];

  if (loading || settingsLoading) return <div className="flex-1 pt-20 text-center flex justify-center"><div className="w-10 h-10 border-4 border-gray-200 border-t-blue-600 rounded-full animate-spin"></div></div>;

  return (
    <div className="flex-1 py-10 bg-[#0a0a0a] min-h-screen text-white">
      <div className="container mx-auto px-6 max-w-6xl animate-[fadeIn_0.5s_ease-out]">
        <div className="mb-8 border-b border-white/10 pb-4">
          <h1 className="text-3xl font-extrabold text-white">{t('admin.dashboard')}</h1>
          <p className="text-white/40 mt-2">{t('admin.overview')}</p>
        </div>

        {/* Tab Switcher */}
        <div className="flex items-center gap-3 mb-8 border-b border-white/10 pb-4">
          <button
            onClick={() => setActiveTab('overview')}
            className={`flex items-center gap-2 px-5 py-2.5 rounded-xl font-bold text-xs uppercase tracking-wider transition-all ${
              activeTab === 'overview'
                ? 'bg-[#00b14f] text-white shadow-lg shadow-[#00b14f]/20'
                : 'bg-white/5 text-white/50 hover:bg-white/10 hover:text-white'
            }`}
          >
            <BarChart3 className="w-4 h-4" />
            Tổng Quan Doanh Thu & Cấu Hình
          </button>
          <button
            onClick={() => setActiveTab('agent-monitor')}
            className={`flex items-center gap-2 px-5 py-2.5 rounded-xl font-bold text-xs uppercase tracking-wider transition-all ${
              activeTab === 'agent-monitor'
                ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-600/20'
                : 'bg-white/5 text-white/50 hover:bg-white/10 hover:text-white'
            }`}
          >
            <Bot className="w-4 h-4 text-amber-300" />
            AI Agent & RAG Performance Monitor
          </button>
        </div>

        {activeTab === 'agent-monitor' && <AgentMonitorTab />}

        {activeTab === 'overview' && (
          <>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-6 mb-10">
              <div className="bg-[#111111] p-8 rounded-2xl border border-white/5 shadow-2xl flex flex-col items-center justify-center hover:-translate-y-1 transition-all group">
                <div className="text-4xl font-black text-[#00b14f] mb-2 group-hover:scale-110 transition-transform">{stats?.totalEvents || 0}</div>
                <div className="text-[10px] font-black text-white/30 uppercase tracking-[0.2em]">{t('admin.events')}</div>
              </div>
          <div className="bg-[#111111] p-8 rounded-2xl border border-white/5 shadow-2xl flex flex-col items-center justify-center hover:-translate-y-1 transition-all group">
            <div className="text-4xl font-black text-[#00b14f] mb-2 group-hover:scale-110 transition-transform">
              {(stats?.totalRevenue || 0).toLocaleString('vi-VN')}₫
            </div>
            <div className="text-[10px] font-black text-white/30 uppercase tracking-[0.2em]">{t('admin.totalRevenue')}</div>
          </div>
          <div className="bg-[#111111] p-8 rounded-2xl border border-white/5 shadow-2xl flex flex-col items-center justify-center hover:-translate-y-1 transition-all group">
            <div className="text-4xl font-black text-[#00b14f] mb-2 group-hover:scale-110 transition-transform">{stats?.totalUsers || 0}</div>
            <div className="text-[10px] font-black text-white/30 uppercase tracking-[0.2em]">{t('admin.users')}</div>
          </div>
        </div>

        <div className="flex flex-wrap gap-4 mb-10">
          <Link to="/admin/events" className="px-8 py-3 bg-[#00b14f] text-white font-black uppercase tracking-widest text-xs rounded-lg hover:bg-[#008a3d] transition-all shadow-lg shadow-[#00b14f]/20">{t('admin.manageEvents')}</Link>
          <Link to="/admin/events/create" className="px-8 py-3 bg-white/5 text-white/70 border border-white/10 font-black uppercase tracking-widest text-xs rounded-lg hover:bg-white/10 transition-all">{t('admin.createEvent')}</Link>
        </div>

        <div className="bg-[#111111] border border-white/5 rounded-3xl p-10 shadow-2xl backdrop-blur-md">
          <div className="mb-10">
            <h2 className="text-2xl font-black text-white mb-2">{t('admin.categoryImages')}</h2>
            <p className="text-white/40 text-sm max-w-2xl">
              {t('admin.categoryImagesDesc')}
            </p>
          </div>
          
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            {CATEGORY_HERO_KEYS.map(key => {
              const isImg = true; 
              return (
                <div key={key} className="flex flex-col bg-white/5 p-6 rounded-2xl border border-white/5 hover:border-white/10 transition-colors group">
                  <label className="text-[10px] font-black text-[#00b14f] uppercase mb-4 tracking-[0.2em] flex items-center gap-2">
                    <ImageIcon size={14} />
                    {getFriendlyName(key)}
                  </label>
                  
                  {isImg ? (
                    <div className="space-y-4">
                      {formData[key] && (
                        <div className="relative group/img aspect-video rounded-xl overflow-hidden border border-white/10 bg-black">
                          <img src={formData[key]} alt={key} className="w-full h-full object-cover transition-transform duration-500 group-hover/img:scale-110" />
                          <div className="absolute inset-0 bg-black/60 opacity-0 group-hover/img:opacity-100 transition-opacity flex items-center justify-center backdrop-blur-sm">
                            <span className="text-white text-[10px] font-black uppercase tracking-widest border border-white/20 px-3 py-1 rounded-full">{t('admin.details')}</span>
                          </div>
                        </div>
                      )}
                      
                      <div className="relative">
                        <input 
                          type="file" 
                          id={`file-${key}`}
                          className="hidden" 
                          accept="image/*"
                          onChange={(e) => handleFileUpload(key, e)}
                        />
                        <label 
                          htmlFor={`file-${key}`}
                          className="w-full px-4 py-6 border-2 border-dashed border-white/10 rounded-2xl flex flex-col items-center justify-center gap-3 cursor-pointer hover:border-[#00b14f] hover:bg-[#00b14f]/5 transition-all group/upload"
                        >
                          {saving ? (
                            <Loader2 className="animate-spin text-[#00b14f]" size={28} />
                          ) : (
                            <Upload className="text-white/20 group-hover/upload:text-[#00b14f] transition-colors" size={28} />
                          )}
                          <span className="text-[11px] font-black uppercase tracking-widest text-white/30 group-hover/upload:text-[#00b14f] transition-colors">
                            {formData[key] ? t('admin.changeImage') : t('admin.uploadFromDevice')}
                          </span>
                        </label>
                      </div>
                    </div>
                  ) : (
                    <input 
                      type="text" 
                      className="w-full px-4 py-3 bg-white/5 border border-white/10 rounded-xl focus:outline-none focus:border-[#00b14f] transition-all text-sm font-medium text-white" 
                      value={formData[key] || ''} 
                      onChange={(e) => handleSettingChange(key, e.target.value)}
                    />
                  )}
                </div>
              );
            })}
          </div>
          
          <div className="mt-12 pt-8 border-t border-white/5 flex justify-end">
            <button 
              className="px-10 py-4 bg-[#00b14f] text-white font-black uppercase tracking-widest text-xs rounded-xl hover:bg-[#008a3d] transition-all shadow-xl shadow-[#00b14f]/30 disabled:opacity-50" 
              onClick={handleSaveSettings}
              disabled={saving}
            >
              {saving ? t('admin.saving') : t('admin.saveSettings')}
            </button>
          </div>
        </div>
      </>
    )}

      </div>
    </div>
  );
}
