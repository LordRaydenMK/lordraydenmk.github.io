interface ViewModel

interface Fragment {

    fun onViewCreated()
}

interface LiveData<T> {

    fun observe(f: (T) -> Unit)
}

class MutableLiveData<T> : LiveData<T> {

    override fun observe(f: (T) -> Unit) {
        
    }
}