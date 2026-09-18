import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { adminApi, eventApi } from '../../api';
import { useLanguage } from '../../i18n';

export default function AdminEventEditPage() {
  const { id } = useParams<{ id: string }>();
  const [form, setForm] = useState<{
    name: string; description: string; venue: string; address: string; bannerUrl: string;
    category: string; eventDate: string; saleStartTime: string; saleEndTime: string;
    queueEnabled: boolean; queueBatchSize: number; hot: boolean; images: string[];
  }>({
    name: '', description: '', venue: '', address: '', bannerUrl: '',
    category: 'LIVE_MUSIC',
    eventDate: '', saleStartTime: '', saleEndTime: '',
    queueEnabled: false, queueBatchSize: 50,
    hot: false, images: []
  });
  const [uploadingImages, setUploadingImages] = useState(false);
  const [error, setError] = useState('');
  const [dateErrors, setDateErrors] = useState<{ eventDate?: string, saleStartTime?: string, saleEndTime?: string }>({});
  const [loading, setLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const navigate = useNavigate();
  const { t } = useLanguage();

  useEffect(() => {
    if (id) {
      eventApi.get(Number(id)).then(res => {
        const ev = res.data.data;
        setForm({
          name: ev.name || '',
          description: ev.description || '',
          venue: ev.venue || '',
          address: ev.address || '',
          bannerUrl: ev.bannerUrl || '',
          category: ev.category || 'LIVE_MUSIC',
          eventDate: ev.eventDate ? ev.eventDate.substring(0, 16) : '',
          saleStartTime: ev.saleStartTime ? ev.saleStartTime.substring(0, 16) : '',
          saleEndTime: ev.saleEndTime ? ev.saleEndTime.substring(0, 16) : '',
          queueEnabled: ev.queueEnabled || false,
          queueBatchSize: ev.queueBatchSize || 50,
          hot: ev.hot || false,
          images: ev.images || []
        });
      }).catch(() => {
        setError('Không thể tải thông tin sự kiện');
      }).finally(() => setInitialLoading(false));
    }
  }, [id]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const target = e.target;
    setForm({
      ...form,
      [target.name]: target.type === 'checkbox' ? (target as HTMLInputElement).checked : target.value
    });
  };

  useEffect(() => {
    const errors: { eventDate?: string, saleStartTime?: string, saleEndTime?: string } = {};
    const now = new Date();
    
    if (form.eventDate) {
      const eventD = new Date(form.eventDate);
      if (eventD <= now) {
        errors.eventDate = t('admin.val.eventDatePast');
      }
    }
    
    if (form.saleStartTime && form.saleEndTime) {
      const start = new Date(form.saleStartTime);
      const end = new Date(form.saleEndTime);
      if (start >= end) {
        errors.saleStartTime = t('admin.val.saleStartAfterEnd');
      }
      if (form.eventDate) {
        const eventD = new Date(form.eventDate);
        if (end >= eventD) {
          errors.saleEndTime = t('admin.val.saleEndAfterEvent');
        }
      }
    }
    setDateErrors(errors);
  }, [form.eventDate, form.saleStartTime, form.saleEndTime]);

  const handleImageUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!e.target.files) return;
    const files = Array.from(e.target.files);
    
    if (form.images.length + files.length > 10) {
      setError(t('admin.val.maxImages'));
      return;
    }

    setUploadingImages(true);
    setError('');
    
    try {
      const newImageUrls: string[] = [];
      for (const file of files) {
        const formData = new FormData();
        formData.append('file', file);
        const res = await adminApi.uploadImage(formData);
        newImageUrls.push(res.data.data);
      }
      setForm(prev => ({ 
        ...prev, 
        images: [...prev.images, ...newImageUrls],
        bannerUrl: prev.bannerUrl || newImageUrls[0] || '' // Auto set banner
      }));
    } catch (err: any) {
      setError(t('admin.val.uploadError'));
    } finally {
      setUploadingImages(false);
      e.target.value = '';
    }
  };

  const handleRemoveImage = (index: number) => {
    setForm(prev => {
      const newImages = [...prev.images];
      newImages.splice(index, 1);
      return { ...prev, images: newImages, bannerUrl: newImages[0] || '' };
    });
  };

  const handleUpdateEvent = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!id) return;

    if (Object.keys(dateErrors).length > 0) {
      setError(t('admin.val.requiredFields'));
      return;
    }

    if (!form.images || form.images.length === 0) {
      setError(t('admin.val.requireImage'));
      return;
    }

    setLoading(true);
    setError('');
    try {
      await adminApi.updateEvent(Number(id), {
        ...form,
        queueBatchSize: Number(form.queueBatchSize)
      });
      alert('Cập nhật sự kiện thành công!');
      navigate('/admin/events');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to update event');
    } finally {
      setLoading(false);
    }
  };

  if (initialLoading) return <div className="flex-1 pt-20 text-center flex justify-center"><div className="w-10 h-10 border-4 border-gray-200 border-t-blue-600 rounded-full animate-spin"></div></div>;

  return (
    <div className="flex-1 py-10 bg-[#0a0a0a] min-h-screen text-white">
      <div className="container mx-auto px-6 max-w-3xl animate-[fadeIn_0.5s_ease-out]">
        <div className="mb-8 border-b border-white/10 pb-4 flex justify-between items-end">
          <div>
            <h1 className="text-3xl font-extrabold mb-2 text-white">{t('admin.editEvent')}</h1>
            <p className="text-white/40 font-medium">{t('admin.editEventDesc')}{id}</p>
          </div>
          <button onClick={() => navigate('/admin/events')} className="px-6 py-2 bg-white/5 text-white/70 font-bold rounded-lg border border-white/10 hover:bg-white/10 transition-colors">
            {t('admin.goBack')}
          </button>
        </div>

        {error && <div className="bg-red-500/10 text-red-500 p-4 rounded-lg border border-red-500/20 mb-6 font-medium">{error}</div>}

        <div className="bg-[#111111] border border-white/5 rounded-2xl shadow-2xl p-8 max-w-2xl mx-auto backdrop-blur-md">
          <form onSubmit={handleUpdateEvent}>
            <div className="mb-5">
              <label className="block text-xs font-black uppercase tracking-widest text-white/40 mb-2">{t('admin.eventName')}</label>
              <input className="w-full px-4 py-3 bg-white/5 border border-white/10 rounded-lg focus:outline-none focus:border-[#00b14f] transition-all text-white placeholder:text-white/20" name="name" value={form.name} onChange={handleChange} required
                placeholder={t('admin.eventNamePlaceholder')} />
            </div>
            <div className="mb-5">
              <label className="block text-xs font-black uppercase tracking-widest text-white/40 mb-2">{t('admin.description')}</label>
              <textarea className="w-full px-4 py-3 bg-white/5 border border-white/10 rounded-lg focus:outline-none focus:border-[#00b14f] transition-all text-white min-h-[100px] placeholder:text-white/20" name="description" value={form.description} onChange={handleChange}
                placeholder={t('admin.descriptionPlaceholder')} />
            </div>
            <div className="mb-5">
              <label className="block text-xs font-black uppercase tracking-widest text-white/40 mb-2">Category</label>
              <select className="w-full px-4 py-3 bg-white/5 border border-white/10 rounded-lg focus:outline-none focus:border-[#00b14f] transition-all text-white cursor-pointer appearance-none" name="category" value={form.category} onChange={handleChange}>
                <option value="LIVE_MUSIC" className="bg-[#1a1a1a]">{t('nav.concerts') || 'Âm nhạc'}</option>
                <option value="ARTS" className="bg-[#1a1a1a]">{t('nav.arts') || 'Nghệ thuật'}</option>
                <option value="WORKSHOP" className="bg-[#1a1a1a]">{t('nav.workshop') || 'Hội thảo'}</option>
                <option value="EXPERIENCE" className="bg-[#1a1a1a]">{t('nav.experience') || 'Trải nghiệm'}</option>
                <option value="SPORTS" className="bg-[#1a1a1a]">{t('nav.sports') || 'Thể thao'}</option>
                <option value="OTHER" className="bg-[#1a1a1a]">{t('nav.other') || 'Khác'}</option>
              </select>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5 mb-5">
              <div>
                <label className="block text-xs font-black uppercase tracking-widest text-white/40 mb-2">{t('admin.venue')}</label>
                <input className="w-full px-4 py-3 bg-white/5 border border-white/10 rounded-lg focus:outline-none focus:border-[#00b14f] transition-all text-white placeholder:text-white/20" name="venue" value={form.venue} onChange={handleChange}
                  placeholder={t('admin.venuePlaceholder')} />
              </div>
              <div>
                <label className="block text-xs font-black uppercase tracking-widest text-white/40 mb-2">{t('admin.address')}</label>
                <input className="w-full px-4 py-3 bg-white/5 border border-white/10 rounded-lg focus:outline-none focus:border-[#00b14f] transition-all text-white placeholder:text-white/20" name="address" value={form.address} onChange={handleChange}
                  placeholder={t('admin.addressPlaceholder')} />
              </div>
            </div>
            <div className="mb-5">
              <label className="block text-xs font-black uppercase tracking-widest text-white/40 mb-2">{t('admin.eventDate')}</label>
              <input className="w-full px-4 py-3 bg-white/5 border border-white/10 rounded-lg focus:outline-none focus:border-[#00b14f] transition-all text-white" name="eventDate" type="datetime-local" value={form.eventDate} onChange={handleChange} required />
              {dateErrors.eventDate && <p className="text-red-500 text-sm mt-1">{dateErrors.eventDate}</p>}
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5 mb-5">
              <div>
                <label className="block text-xs font-black uppercase tracking-widest text-white/40 mb-2">{t('admin.saleStart')}</label>
                <input className="w-full px-4 py-3 bg-white/5 border border-white/10 rounded-lg focus:outline-none focus:border-[#00b14f] transition-all text-white" name="saleStartTime" type="datetime-local" value={form.saleStartTime} onChange={handleChange} />
                {dateErrors.saleStartTime && <p className="text-red-500 text-sm mt-1">{dateErrors.saleStartTime}</p>}
              </div>
              <div>
                <label className="block text-xs font-black uppercase tracking-widest text-white/40 mb-2">{t('admin.saleEnd')}</label>
                <input className="w-full px-4 py-3 bg-white/5 border border-white/10 rounded-lg focus:outline-none focus:border-[#00b14f] transition-all text-white" name="saleEndTime" type="datetime-local" value={form.saleEndTime} onChange={handleChange} />
                {dateErrors.saleEndTime && <p className="text-red-500 text-sm mt-1">{dateErrors.saleEndTime}</p>}
              </div>
            </div>
            
            <div className="mb-5 flex flex-wrap items-center gap-8">
              <div className="flex items-center gap-3">
                <input type="checkbox" className="w-5 h-5 accent-[#00b14f] bg-white/5 border-white/10 rounded" name="queueEnabled" checked={form.queueEnabled}
                  onChange={handleChange} id="queueEnabled" />
                <label htmlFor="queueEnabled" className="cursor-pointer font-black text-[11px] uppercase tracking-widest text-white/70">
                  {t('admin.enableQueue')}
                </label>
              </div>
              
              <div className="flex items-center gap-3">
                <input type="checkbox" className="w-5 h-5 accent-red-600 bg-white/5 border-white/10 rounded" name="hot" checked={form.hot}
                  onChange={handleChange} id="hot" />
                <label htmlFor="hot" className="cursor-pointer font-black text-[11px] uppercase tracking-widest text-red-500">
                  {t('admin.markHot')}
                </label>
              </div>
            </div>

            {/* IMAGE UPLOAD */}
            <div className="mb-5 p-5 border-2 border-dashed border-white/10 rounded-xl bg-white/5">
              <label className="block text-xs font-black uppercase tracking-widest text-white/40 mb-3">{t('admin.eventImages')}</label>
              <div className="flex flex-wrap gap-4 mb-4">
                {form.images.map((url, idx) => (
                  <div key={idx} className="relative w-24 h-24 rounded-lg overflow-hidden border border-white/10 group shadow-lg">
                    <img src={url} alt={`Preview ${idx}`} className="w-full h-full object-cover" />
                    <div className="absolute inset-0 bg-black/70 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                      <button type="button" onClick={() => handleRemoveImage(idx)} className="text-white bg-red-600 p-1.5 rounded-full hover:bg-red-700">
                        {t('admin.delete')}
                      </button>
                    </div>
                  </div>
                ))}
                
                {form.images.length < 10 && (
                  <div className="w-24 h-24 rounded-lg border-2 border-dashed border-white/10 flex flex-col items-center justify-center text-white/20 relative hover:bg-white/10 transition-colors">
                    <span className="text-2xl mb-1">+</span>
                    <span className="text-[10px] font-black uppercase tracking-wider">{t('admin.upload')}</span>
                    <input type="file" multiple accept="image/*" onChange={handleImageUpload} className="absolute inset-0 w-full h-full opacity-0 cursor-pointer" disabled={uploadingImages} />
                  </div>
                )}
              </div>
              {uploadingImages && <div className="text-sm text-[#00b14f] font-bold animate-pulse mb-2">{t('admin.uploading')}</div>}
              <div className="text-[10px] text-white/30 uppercase tracking-tight">{t('admin.firstImageBanner')}</div>
            </div>
            
            {form.queueEnabled && (
              <div className="mb-5">
                <label className="block text-xs font-black uppercase tracking-widest text-white/40 mb-2">{t('admin.batchSize')}</label>
                <input className="w-full px-4 py-3 bg-white/5 border border-white/10 rounded-lg focus:outline-none focus:border-[#00b14f] transition-all text-white" name="queueBatchSize" type="number" value={form.queueBatchSize}
                  onChange={handleChange} min="1" />
              </div>
            )}
            
            <button className="w-full py-4 mt-2 bg-[#00b14f] text-white font-black uppercase tracking-widest rounded-lg hover:bg-[#008a3d] transition-all shadow-xl shadow-[#00b14f]/20 disabled:opacity-50" type="submit" disabled={loading}>
              {loading ? t('admin.updating') : t('admin.updateEventBtn')}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
